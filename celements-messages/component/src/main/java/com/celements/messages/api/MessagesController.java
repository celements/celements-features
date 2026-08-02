package com.celements.messages.api;

import java.io.IOException;

import javax.inject.Inject;

import org.apache.velocity.VelocityContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.messages.service.MessageService;
import com.celements.spring.security.AuthenticatedBaseController;
import com.celements.web.service.IPrepareVelocityContext;

@RestController
@RequestMapping("/v1/messages")
@PreAuthorize("permitAll()")
public class MessagesController extends AuthenticatedBaseController {

  private static final String JSON_MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";

  private final VelocityManager velocityManager;
  private final IPrepareVelocityContext prepareVelocityContext;
  private final MessageService messageService;

  @Inject
  public MessagesController(VelocityManager velocityManager,
      IPrepareVelocityContext prepareVelocityContext, MessageService messageService) {
    this.velocityManager = velocityManager;
    this.prepareVelocityContext = prepareVelocityContext;
    this.messageService = messageService;
  }

  @GetMapping(produces = JSON_MEDIA_TYPE)
  public String getMessages() throws IOException, XWikiVelocityException {
    return messageService.getMessages(getPreparedVelocityContext());
  }

  @GetMapping(value = "/validation", produces = JSON_MEDIA_TYPE)
  public String getValidationMessages() throws IOException, XWikiVelocityException {
    return messageService.getValidationMessages(getPreparedVelocityContext());
  }

  private VelocityContext getPreparedVelocityContext() {
    checkAuth();
    VelocityContext velocityContext = velocityManager.getVelocityContext();
    prepareVelocityContext.prepareVelocityContext(velocityContext);
    return velocityContext;
  }
}
