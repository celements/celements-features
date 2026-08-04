package com.celements.messages.api;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xwiki.velocity.VelocityManager;

import com.celements.auth.user.UserService;
import com.celements.common.test.AbstractComponentTest;
import com.celements.messages.service.MessageService;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.celements.web.service.IPrepareVelocityContext;
import com.xpn.xwiki.XWiki;

public class MessagesControllerTest extends AbstractComponentTest {

  private VelocityContext velocityContext;
  private MessagesController controller;

  @Before
  public void prepareTest() throws Exception {
    registerComponentMocks(VelocityManager.class, IPrepareVelocityContext.class,
        MessageService.class, UserService.class, IRightsAccessFacadeRole.class);
    velocityContext = new VelocityContext();
    controller = getBeanTarget(MessagesController.class);
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
    expectAnonymousAuth();
    expectPreparedContext();
    expect(getMock(MessageService.class).getMessages(same(velocityContext)))
        .andReturn("{\"message\":\"value\"}");
    replayDefault();

    assertEquals("{\"message\":\"value\"}", controller.getMessages());

    verifyDefault();
  }

  @Test
  public void testGetValidationMessagesUsesPreparedExistingContext() throws Exception {
    expectAnonymousAuth();
    expectPreparedContext();
    expect(getMock(MessageService.class).getValidationMessages(same(velocityContext)))
        .andReturn("{\"required\":\"Required\"}");
    replayDefault();

    assertEquals("{\"required\":\"Required\"}", controller.getValidationMessages());

    verifyDefault();
  }

  @Test
  public void testServiceFailurePropagates() throws Exception {
    expectAnonymousAuth();
    expectPreparedContext();
    expect(getMock(MessageService.class).getMessages(same(velocityContext)))
        .andThrow(new IOException("failed"));
    replayDefault();

    assertThrows(IOException.class, controller::getMessages);

    verifyDefault();
  }

  private void expectPreparedContext() {
    expect(getMock(VelocityManager.class).getVelocityContext()).andReturn(velocityContext);
    getMock(IPrepareVelocityContext.class).prepareVelocityContext(same(velocityContext));
  }

  private void expectAnonymousAuth() throws Exception {
    expect(getMock(XWiki.class).checkAuth(getXContext())).andReturn(null);
  }

  @SuppressWarnings("unchecked")
  private <T> T getBeanTarget(Class<T> beanClass) throws Exception {
    T bean = getBeanFactory().getBean(beanClass);
    return bean instanceof Advised advised
        ? (T) advised.getTargetSource().getTarget()
        : bean;
  }
}
