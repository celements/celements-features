package com.celements.messages.service;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.FileSystemUtils;
import org.xwiki.velocity.VelocityEngine;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.common.test.AbstractComponentTest;
import com.celements.sajson.JsonBuilder;

public class MessageServiceTest extends AbstractComponentTest {

  private VelocityEngine velocityEngine;
  private VelocityContext velocityContext;
  private MessageService messageService;
  private List<String> evaluatedFragments;
  private Path webRoot;

  @Before
  public void prepareTest() throws Exception {
    webRoot = Path.of(Objects.requireNonNull(getClass().getResource("/")).toURI());
    FileSystemUtils.deleteRecursively(webRoot.resolve("templates/celMessages"));
    registerComponentMocks(VelocityManager.class);
    velocityEngine = createDefaultMock(VelocityEngine.class);
    velocityContext = new VelocityContext();
    messageService = getBeanFactory().getBean(MessageService.class);
    evaluatedFragments = new ArrayList<>();
  }

  @Test
  public void testGetMessagesUsesLexicalDirectFragments() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    String zeta = directory + "zeta.vm";
    createFragment(celements);
    createFragment(zeta);
    createFragment(directory + "nested/ignored.vm");
    expectVelocityEngine();
    expectFragment(celements, " \n", true, builder -> {
      builder.addProperty("string", "value");
      builder.addProperty("boolean", true);
      builder.addProperty("number", 7);
    });
    expectFragment(zeta, "\n", true, builder -> builder.addProperty("zeta", "last"));
    replayDefault();

    String json = messageService.getMessages(velocityContext);

    assertEquals(
        "{\"string\" : \"value\", \"boolean\" : true, \"number\" : 7, "
            + "\"zeta\" : \"last\"}",
        json);
    assertEquals(List.of(celements, zeta), evaluatedFragments);
    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyDefault();
  }

  @Test
  public void testGetValidationMessagesUsesOnlyValidationDirectory() throws Exception {
    String directory = MessageService.VALIDATION_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    createFragment(celements);
    expectVelocityEngine();
    expectFragment(celements, "", true, builder -> builder.addProperty("required", "Required"));
    replayDefault();

    assertEquals("{\"required\" : \"Required\"}",
        messageService.getValidationMessages(velocityContext));

    assertEquals(List.of(celements), evaluatedFragments);
    verifyDefault();
  }

  @Test
  public void testMissingCelementsFragmentFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    createFragment(directory + "product.vm");
    replayDefault();

    assertThrows(IOException.class, () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyDefault();
  }

  @Test
  public void testNonWhitespaceFragmentOutputFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    createFragment(celements);
    expectVelocityEngine();
    expectFragment(celements, "unexpected", true,
        builder -> builder.addProperty("message", "value"));
    replayDefault();

    assertThrows(IOException.class, () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyDefault();
  }

  @Test
  public void testFailedFragmentEvaluationFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    createFragment(celements);
    expectVelocityEngine();
    expectFragment(celements, "", false, builder -> assertNotNull(builder));
    replayDefault();

    assertThrows(XWikiVelocityException.class, () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyDefault();
  }

  @Test
  public void testUnbalancedBuilderDepthFailsCompleteRequest() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    createFragment(celements);
    expectVelocityEngine();
    expectFragment(celements, "", true, builder -> builder.openDictionary("nested"));
    replayDefault();

    assertThrows(IllegalStateException.class,
        () -> messageService.getMessages(velocityContext));

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyDefault();
  }

  @Test
  public void testExistingJsonBuilderContextValueIsRestored() throws Exception {
    String directory = MessageService.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    var previousBuilder = new JsonBuilder();
    velocityContext.put("jsonBuilder", previousBuilder);
    createFragment(celements);
    expectVelocityEngine();
    expectFragment(celements, "", true, builder -> builder.addProperty("message", "value"));
    replayDefault();

    messageService.getMessages(velocityContext);

    assertSame(previousBuilder, velocityContext.get("jsonBuilder"));
    verifyDefault();
  }

  private void createFragment(String path) throws IOException {
    var fragment = webRoot.resolve(path.substring(1));
    Files.createDirectories(fragment.getParent());
    Files.writeString(fragment, "fragment:" + path, StandardCharsets.UTF_8);
  }

  private void expectVelocityEngine() throws XWikiVelocityException {
    expect(getMock(VelocityManager.class).getVelocityEngine()).andReturn(velocityEngine);
  }

  private void expectFragment(String path, String output, boolean result,
      Consumer<JsonBuilder> builderAction) throws Exception {
    String source = "fragment:" + path;
    expect(velocityEngine.evaluate(same(velocityContext), isA(StringWriter.class), eq(path),
        eq(source))).andAnswer(() -> {
          evaluatedFragments.add(path);
          builderAction.accept((JsonBuilder) velocityContext.get("jsonBuilder"));
          ((StringWriter) getCurrentArguments()[1]).write(output);
          return result;
        });
  }
}
