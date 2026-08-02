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

import com.celements.messages.service.MessageService;

public class MessagesScriptServiceTest {

  private VelocityManager velocityManager;
  private MessageService messageService;
  private VelocityContext velocityContext;
  private MessagesScriptService scriptService;

  @Before
  public void prepareTest() {
    velocityManager = createMock(VelocityManager.class);
    messageService = createMock(MessageService.class);
    velocityContext = new VelocityContext();
    scriptService = new MessagesScriptService(velocityManager, messageService);
  }

  @Test
  public void testComponentRegistration() {
    assertTrue(scriptService instanceof ScriptService);
    assertEquals(MessagesScriptService.NAME,
        MessagesScriptService.class.getAnnotation(Component.class).value());
  }

  @Test
  public void testGetMessagesUsesActiveExistingContext() throws Exception {
    expect(velocityManager.getVelocityContext()).andReturn(velocityContext);
    expect(messageService.getMessages(same(velocityContext))).andReturn("{\"message\":\"value\"}");
    replayAll();

    assertEquals("{\"message\":\"value\"}", scriptService.getMessages());

    verifyAll();
  }

  @Test
  public void testGetValidationMessagesUsesActiveExistingContext() throws Exception {
    expect(velocityManager.getVelocityContext()).andReturn(velocityContext);
    expect(messageService.getValidationMessages(same(velocityContext)))
        .andReturn("{\"required\":\"Required\"}");
    replayAll();

    assertEquals("{\"required\":\"Required\"}", scriptService.getValidationMessages());

    verifyAll();
  }

  @Test
  public void testServiceFailurePropagates() throws Exception {
    expect(velocityManager.getVelocityContext()).andReturn(velocityContext);
    expect(messageService.getMessages(same(velocityContext))).andThrow(new IOException("failed"));
    replayAll();

    assertThrows(IOException.class, scriptService::getMessages);

    verifyAll();
  }

  private void replayAll() {
    replay(velocityManager, messageService);
  }

  private void verifyAll() {
    verify(velocityManager, messageService);
  }
}
