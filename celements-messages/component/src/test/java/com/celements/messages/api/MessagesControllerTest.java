package com.celements.messages.api;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.servlet.ServletContext;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xwiki.velocity.VelocityEngine;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.auth.user.User;
import com.celements.sajson.JsonBuilder;
import com.celements.web.service.IPrepareVelocityContext;

public class MessagesControllerTest {

  private ServletContext servletContext;
  private VelocityManager velocityManager;
  private VelocityEngine velocityEngine;
  private IPrepareVelocityContext prepareVelocityContext;
  private VelocityContext velocityContext;
  private TestMessagesController controller;
  private List<String> evaluatedFragments;

  @Before
  public void prepareTest() {
    servletContext = createMock(ServletContext.class);
    velocityManager = createMock(VelocityManager.class);
    velocityEngine = createMock(VelocityEngine.class);
    prepareVelocityContext = createMock(IPrepareVelocityContext.class);
    velocityContext = new VelocityContext();
    controller = new TestMessagesController(servletContext, velocityManager,
        prepareVelocityContext);
    evaluatedFragments = new ArrayList<>();
  }

  @Test
  public void testEndpointMappings() throws Exception {
    assertNotNull(MessagesController.class.getAnnotation(RestController.class));
    assertArrayEquals(new String[] { "/v1/messages" },
        MessagesController.class.getAnnotation(RequestMapping.class).value());
    assertEquals("permitAll()", MessagesController.class.getAnnotation(PreAuthorize.class).value());
    GetMapping general = MessagesController.class.getMethod("getMessages")
        .getAnnotation(GetMapping.class);
    assertArrayEquals(new String[0], general.value());
    assertArrayEquals(new String[] { "application/json;charset=UTF-8" }, general.produces());
    GetMapping validation = MessagesController.class.getMethod("getValidationMessages")
        .getAnnotation(GetMapping.class);
    assertArrayEquals(new String[] { "/validation" }, validation.value());
    assertArrayEquals(new String[] { "application/json;charset=UTF-8" }, validation.produces());
  }

  @Test
  public void testGetMessagesUsesPreparedExistingContextAndLexicalDirectFragments()
      throws Exception {
    String directory = MessagesController.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    String zeta = directory + "zeta.vm";
    expectContextAndDiscovery(directory, Set.of(zeta, directory + "nested/ignored.vm", celements));
    expectFragment(celements, " \n", true, builder -> {
      builder.addProperty("string", "value");
      builder.addProperty("boolean", true);
      builder.addProperty("number", 7);
    });
    expectFragment(zeta, "\n", true, builder -> builder.addProperty("zeta", "last"));
    replayAll();

    String json = controller.getMessages();

    assertEquals(
        "{\"string\" : \"value\", \"boolean\" : true, \"number\" : 7, " + "\"zeta\" : \"last\"}",
        json);
    assertTrue(controller.isCheckAuthCalled());
    assertEquals(List.of(celements, zeta), evaluatedFragments);
    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testGetValidationMessagesUsesOnlyValidationDirectory() throws Exception {
    String directory = MessagesController.VALIDATION_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectContextAndDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", true, builder -> builder.addProperty("required", "Required"));
    replayAll();

    assertEquals("{\"required\" : \"Required\"}", controller.getValidationMessages());

    assertEquals(List.of(celements), evaluatedFragments);
    verifyAll();
  }

  @Test
  public void testMissingCelementsFragmentFailsCompleteRequest() {
    String directory = MessagesController.GENERAL_FRAGMENT_DIRECTORY;
    expectContextAndDiscoveryWithoutEngine(directory, Set.of(directory + "product.vm"));
    replayAll();

    assertThrows(IOException.class, controller::getMessages);

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testNonWhitespaceFragmentOutputFailsCompleteRequest() throws Exception {
    String directory = MessagesController.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectContextAndDiscovery(directory, Set.of(celements));
    expectFragment(celements, "unexpected", true,
        builder -> builder.addProperty("message", "value"));
    replayAll();

    assertThrows(IOException.class, controller::getMessages);

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testFailedFragmentEvaluationFailsCompleteRequest() throws Exception {
    String directory = MessagesController.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectContextAndDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", false, builder -> assertNotNull(builder));
    replayAll();

    assertThrows(XWikiVelocityException.class, controller::getMessages);

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testUnbalancedBuilderDepthFailsCompleteRequest() throws Exception {
    String directory = MessagesController.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    expectContextAndDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", true, builder -> builder.openDictionary("nested"));
    replayAll();

    assertThrows(IllegalStateException.class, controller::getMessages);

    assertFalse(velocityContext.containsKey("jsonBuilder"));
    verifyAll();
  }

  @Test
  public void testExistingJsonBuilderContextValueIsRestored() throws Exception {
    String directory = MessagesController.GENERAL_FRAGMENT_DIRECTORY;
    String celements = directory + "celements.vm";
    var previousBuilder = new JsonBuilder();
    velocityContext.put("jsonBuilder", previousBuilder);
    expectContextAndDiscovery(directory, Set.of(celements));
    expectFragment(celements, "", true, builder -> builder.addProperty("message", "value"));
    replayAll();

    controller.getMessages();

    assertSame(previousBuilder, velocityContext.get("jsonBuilder"));
    verifyAll();
  }

  private void expectContextAndDiscovery(String directory, Set<String> resources)
      throws XWikiVelocityException {
    expectContextAndDiscoveryWithoutEngine(directory, resources);
    expect(velocityManager.getVelocityEngine()).andReturn(velocityEngine);
  }

  private void expectContextAndDiscoveryWithoutEngine(String directory, Set<String> resources) {
    expect(velocityManager.getVelocityContext()).andReturn(velocityContext);
    prepareVelocityContext.prepareVelocityContext(same(velocityContext));
    expect(servletContext.getResourcePaths(directory)).andReturn(resources);
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
    replay(servletContext, velocityManager, velocityEngine, prepareVelocityContext);
  }

  private void verifyAll() {
    verify(servletContext, velocityManager, velocityEngine, prepareVelocityContext);
  }

  private static final class TestMessagesController extends MessagesController {

    private boolean checkAuthCalled;

    TestMessagesController(ServletContext servletContext, VelocityManager velocityManager,
        IPrepareVelocityContext prepareVelocityContext) {
      super(servletContext, velocityManager, prepareVelocityContext);
    }

    @Override
    protected Optional<User> checkAuth() {
      checkAuthCalled = true;
      return Optional.empty();
    }

    boolean isCheckAuthCalled() {
      return checkAuthCalled;
    }
  }
}
