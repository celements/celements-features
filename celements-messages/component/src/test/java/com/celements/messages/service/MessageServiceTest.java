package com.celements.messages.service;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.servlet.ServletContext;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.velocity.VelocityEngine;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.sajson.JsonBuilder;

public class MessageServiceTest {

  private ServletContext servletContext;
  private VelocityManager velocityManager;
  private VelocityEngine velocityEngine;
  private VelocityContext velocityContext;
  private MessageService messageService;
  private List<String> evaluatedFragments;

  @Before
  public void prepareTest() {
    servletContext = createMock(ServletContext.class);
    velocityManager = createMock(VelocityManager.class);
    velocityEngine = createMock(VelocityEngine.class);
    velocityContext = new VelocityContext();
    messageService = new MessageService(servletContext, velocityManager);
    evaluatedFragments = new ArrayList<>();
  }

  @Test
  public void testGetMessagesUsesLexicalDirectFragments() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    String zeta = directory + "zeta.vm";
    expectDiscovery(directory, Set.of(zeta, directory + "nested/ignored.vm", celements));
    expectFragment(celements, " \n", true, builder -> {
      builder.addProperty("string", "value");
      builder.addProperty("boolean", true);
      builder.addProperty("number", 7);
    });
    expectFragment(zeta, "\n", true, builder -> builder.addProperty("zeta", "last"));
    replayAll();

    String json = messageService.getMessages(velocityContext);

    assertEquals(
        "{\"string\" : \"value\", \"boolean\" : true, \"number\" : 7, "
            + "\"zeta\" : \"last\"}",
        json);
    assertEquals(List.of(celements, zeta), evaluatedFragments);
    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testGetValidationMessagesUsesOnlyValidationDirectory() throws Exception {
    String directory = MessageService.VALIDATION_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", true, builder -> builder.addProperty("required", "Required"));
    replayAll();

    assertEquals("{\"required\" : \"Required\"}",
        messageService.getValidationMessages(velocityContext));

    assertEquals(List.of(celements), evaluatedFragments);
    verifyAll();
  }

  @Test
  public void testMissingCelementsFragmentFailsCompleteRequest() {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    expect(servletContext.getResourcePaths(directory)).andReturn(Set.of(directory + "product.vm"));
    replayAll();

    assertThrows(IOException.class, () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testNonWhitespaceFragmentOutputFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectDiscovery(directory, Set.of(celements));
    expectFragment(celements, "unexpected", true,
        builder -> builder.addProperty("message", "value"));
    replayAll();

    assertThrows(IOException.class, () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testFailedFragmentEvaluationFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", false, builder -> assertNotNull(builder));
    replayAll();

    assertThrows(XWikiVelocityException.class, () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testUnbalancedBuilderDepthFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", true, builder -> builder.openDictionary("nested"));
    replayAll();

    assertThrows(IllegalStateException.class,
        () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testExistingJsonBuilderContextValueIsRestored() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    var previousBuilder = new JsonBuilder();
    velocityContext.put("jsonBuilder", previousBuilder);
    expectDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", true, builder -> builder.addProperty("message", "value"));
    replayAll();

    messageService.getMessages(velocityContext);

    assertSame(previousBuilder, velocityContext.get("jsonBuilder"));
    verifyAll();
  }

  private void expectDiscovery(String directory, Set<String> resources)
      throws XWikiVelocityException {
    expect(servletContext.getResourcePaths(directory)).andReturn(resources);
    expect(velocityManager.getVelocityEngine()).andReturn(velocityEngine);
  }

  private void expectFragment(String path, String output, boolean result,
      Consumer<JsonBuilder> builderAction) throws Exception {
    String source = "fragment:" + path;
    expect(servletContext.getResourceAsStream(path))
        .andReturn(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    expect(velocityEngine.evaluate(same(velocityContext), isA(StringWriter.class), eq(path),
        eq(source))).andAnswer(() -> {
          evaluatedFragments.add(path);
          builderAction.accept((JsonBuilder) velocityContext.get("jsonBuilder"));
          ((StringWriter) getCurrentArguments()[1]).write(output);
          return result;
        });
  }

  private void replayAll() {
    replay(servletContext, velocityManager, velocityEngine);
  }

  private void verifyAll() {
    verify(servletContext, velocityManager, velocityEngine);
  }
}
