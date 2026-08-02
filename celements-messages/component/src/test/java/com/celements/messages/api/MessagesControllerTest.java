package com.celements.messages.api;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Optional;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xwiki.velocity.VelocityManager;

import com.celements.auth.user.User;
import com.celements.messages.service.MessageService;
import com.celements.web.service.IPrepareVelocityContext;

public class MessagesControllerTest {

  private VelocityManager velocityManager;
  private IPrepareVelocityContext prepareVelocityContext;
  private MessageService messageService;
  private VelocityContext velocityContext;
  private TestMessagesController controller;

  @Before
  public void prepareTest() {
    velocityManager = createMock(VelocityManager.class);
    prepareVelocityContext = createMock(IPrepareVelocityContext.class);
    messageService = createMock(MessageService.class);
    velocityContext = new VelocityContext();
    controller = new TestMessagesController(velocityManager, prepareVelocityContext,
        messageService);
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
  public void testGetMessagesUsesPreparedExistingContext() throws Exception {
    expectPreparedContext();
    expect(messageService.getMessages(same(velocityContext))).andReturn("{\"message\":\"value\"}");
    replayAll();

    assertEquals("{\"message\":\"value\"}", controller.getMessages());

    assertTrue(controller.isCheckAuthCalled());
    verifyAll();
  }

  @Test
  public void testGetValidationMessagesUsesPreparedExistingContext() throws Exception {
    expectPreparedContext();
    expect(messageService.getValidationMessages(same(velocityContext)))
        .andReturn("{\"required\":\"Required\"}");
    replayAll();

    assertEquals("{\"required\":\"Required\"}", controller.getValidationMessages());

    assertTrue(controller.isCheckAuthCalled());
    verifyAll();
  }

  @Test
  public void testServiceFailurePropagates() throws Exception {
    expectPreparedContext();
    expect(messageService.getMessages(same(velocityContext))).andThrow(new IOException("failed"));
    replayAll();

    assertThrows(IOException.class, controller::getMessages);

    verifyAll();
  }

  private void expectPreparedContext() {
    expect(velocityManager.getVelocityContext()).andReturn(velocityContext);
    prepareVelocityContext.prepareVelocityContext(same(velocityContext));
  }

  private void replayAll() {
    replay(velocityManager, prepareVelocityContext, messageService);
  }

  private void verifyAll() {
    verify(velocityManager, prepareVelocityContext, messageService);
  }

  private static final class TestMessagesController extends MessagesController {

    private boolean checkAuthCalled;

    TestMessagesController(VelocityManager velocityManager,
        IPrepareVelocityContext prepareVelocityContext, MessageService messageService) {
      super(velocityManager, prepareVelocityContext, messageService);
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
