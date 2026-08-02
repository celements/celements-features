package com.celements.messages.script;

import java.io.IOException;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.script.service.ScriptService;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.messages.service.MessageService;

@Component(MessagesScriptService.NAME)
public class MessagesScriptService implements ScriptService {

  public static final String NAME = "celementsMessages";

  private final VelocityManager velocityManager;
  private final MessageService messageService;

  @Inject
  public MessagesScriptService(VelocityManager velocityManager, MessageService messageService) {
    this.velocityManager = velocityManager;
    this.messageService = messageService;
  }

  public String getMessages() throws IOException, XWikiVelocityException {
    return messageService.getMessages(velocityManager.getVelocityContext());
  }

  public String getValidationMessages() throws IOException, XWikiVelocityException {
    return messageService.getValidationMessages(velocityManager.getVelocityContext());
  }
}
