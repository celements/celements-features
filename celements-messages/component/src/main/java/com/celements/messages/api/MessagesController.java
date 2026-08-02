package com.celements.messages.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.ServletContext;

import org.apache.velocity.VelocityContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xwiki.velocity.VelocityEngine;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.sajson.JsonBuilder;
import com.celements.spring.security.AuthenticatedBaseController;
import com.celements.web.service.IPrepareVelocityContext;

@RestController
@RequestMapping("/v1/messages")
@PreAuthorize("permitAll()")
public class MessagesController extends AuthenticatedBaseController {

  static final String GENERAL_FRAGMENT_DIRECTORY = "/templates/celMessages/general/";
  static final String VALIDATION_FRAGMENT_DIRECTORY = "/templates/celMessages/validation/";
  private static final String REQUIRED_FRAGMENT_NAME = "celements.vm";
  private static final String JSON_BUILDER_CONTEXT_KEY = "jsonBuilder";
  private static final String JSON_MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8";

  private final ServletContext servletContext;
  private final VelocityManager velocityManager;
  private final IPrepareVelocityContext prepareVelocityContext;

  @Inject
  public MessagesController(ServletContext servletContext, VelocityManager velocityManager,
      IPrepareVelocityContext prepareVelocityContext) {
    this.servletContext = servletContext;
    this.velocityManager = velocityManager;
    this.prepareVelocityContext = prepareVelocityContext;
  }

  @GetMapping(produces = JSON_MEDIA_TYPE)
  public String getMessages() throws IOException, XWikiVelocityException {
    return renderFragments(GENERAL_FRAGMENT_DIRECTORY);
  }

  @GetMapping(value = "/validation", produces = JSON_MEDIA_TYPE)
  public String getValidationMessages() throws IOException, XWikiVelocityException {
    return renderFragments(VALIDATION_FRAGMENT_DIRECTORY);
  }

  private String renderFragments(String directory) throws IOException, XWikiVelocityException {
    checkAuth();
    VelocityContext velocityContext = velocityManager.getVelocityContext();
    prepareVelocityContext.prepareVelocityContext(velocityContext);
    var jsonBuilder = new JsonBuilder();
    jsonBuilder.openDictionary();
    List<String> fragments = discoverFragments(directory);
    requireBaseFragment(directory, fragments);
    boolean hadJsonBuilder = velocityContext.containsKey(JSON_BUILDER_CONTEXT_KEY);
    Object previousJsonBuilder = velocityContext.get(JSON_BUILDER_CONTEXT_KEY);
    velocityContext.put(JSON_BUILDER_CONTEXT_KEY, jsonBuilder);
    try {
      VelocityEngine velocityEngine = velocityManager.getVelocityEngine();
      for (String fragment : fragments) {
        evaluateFragment(velocityEngine, velocityContext, jsonBuilder, fragment);
      }
      jsonBuilder.closeDictionary();
      if (!jsonBuilder.isComplete()) {
        throw new IllegalStateException("Messages JSON builder is incomplete");
      }
      return jsonBuilder.getJSON();
    } finally {
      if (hadJsonBuilder) {
        velocityContext.put(JSON_BUILDER_CONTEXT_KEY, previousJsonBuilder);
      } else {
        velocityContext.remove(JSON_BUILDER_CONTEXT_KEY);
      }
    }
  }

  private List<String> discoverFragments(String directory) {
    return Optional.ofNullable(servletContext.getResourcePaths(directory))
        .orElseGet(Collections::emptySet).stream().filter(path -> isDirectFragment(directory, path))
        .sorted().toList();
  }

  private boolean isDirectFragment(String directory, String path) {
    return path.startsWith(directory) && path.endsWith(".vm")
        && !path.substring(directory.length()).contains("/");
  }

  private void requireBaseFragment(String directory, List<String> fragments) throws IOException {
    String requiredFragment = directory + REQUIRED_FRAGMENT_NAME;
    if (!fragments.contains(requiredFragment)) {
      throw new IOException("Required message fragment not found: " + requiredFragment);
    }
  }

  private void evaluateFragment(VelocityEngine velocityEngine, VelocityContext velocityContext,
      JsonBuilder jsonBuilder, String fragment) throws IOException, XWikiVelocityException {
    int initialDepth = jsonBuilder.getOpenCommandCount();
    var writer = new StringWriter();
    boolean evaluated = velocityEngine.evaluate(velocityContext, writer, fragment,
        getFragmentContent(fragment));
    if (!evaluated) {
      throw new XWikiVelocityException("Failed to evaluate message fragment: " + fragment);
    }
    if (!writer.toString().trim().isEmpty()) {
      throw new IOException("Message fragment emitted response content: " + fragment);
    }
    if (jsonBuilder.getOpenCommandCount() != initialDepth) {
      throw new IllegalStateException(
          "Message fragment left an unbalanced JSON builder: " + fragment);
    }
  }

  private String getFragmentContent(String fragment) throws IOException {
    try (InputStream stream = servletContext.getResourceAsStream(fragment)) {
      if (stream == null) {
        throw new IOException("Message fragment not found: " + fragment);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
