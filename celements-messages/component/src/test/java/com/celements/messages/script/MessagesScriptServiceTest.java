package com.celements.messages.script;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.stereotype.Component;
import org.xwiki.script.service.ScriptService;
import org.xwiki.velocity.VelocityManager;

import com.celements.common.test.AbstractComponentTest;
import com.celements.messages.service.MessageService;

public class MessagesScriptServiceTest extends AbstractComponentTest {

  private VelocityContext velocityContext;
  private MessagesScriptService scriptService;

  @Before
  public void prepareTest() throws Exception {
    registerComponentMocks(VelocityManager.class, MessageService.class);
    velocityContext = new VelocityContext();
    scriptService = getBeanFactory().getBean(MessagesScriptService.class);
  }

  @Test
  public void testComponentRegistration() {
    assertTrue(scriptService instanceof ScriptService);
    assertEquals(MessagesScriptService.NAME,
        MessagesScriptService.class.getAnnotation(Component.class).value());
  }

  @Test
  public void testGetMessagesUsesActiveExistingContext() throws Exception {
    expect(getMock(VelocityManager.class).getVelocityContext()).andReturn(velocityContext);
    expect(getMock(MessageService.class).getMessages(same(velocityContext)))
        .andReturn("{\"message\":\"value\"}");
    replayDefault();

    assertEquals("{\"message\":\"value\"}", scriptService.getMessages());

    verifyDefault();
  }

  @Test
  public void testGetValidationMessagesUsesActiveExistingContext() throws Exception {
    expect(getMock(VelocityManager.class).getVelocityContext()).andReturn(velocityContext);
    expect(getMock(MessageService.class).getValidationMessages(same(velocityContext)))
        .andReturn("{\"required\":\"Required\"}");
    replayDefault();

    assertEquals("{\"required\":\"Required\"}", scriptService.getValidationMessages());

    verifyDefault();
  }

  @Test
  public void testServiceFailurePropagates() throws Exception {
    expect(getMock(VelocityManager.class).getVelocityContext()).andReturn(velocityContext);
    expect(getMock(MessageService.class).getMessages(same(velocityContext)))
        .andThrow(new IOException("failed"));
    replayDefault();

    assertThrows(IOException.class, scriptService::getMessages);

    verifyDefault();
  }
}
