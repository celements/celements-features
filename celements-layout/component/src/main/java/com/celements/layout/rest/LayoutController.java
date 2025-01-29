package com.celements.layout.rest;

import java.util.Objects;

import javax.inject.Inject;

import org.python.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.velocity.VelocityManager;

import com.celements.execution.XWikiExecutionProp;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.context.ModelContext;
import com.celements.model.reference.RefBuilder;
import com.celements.pagelayout.LayoutServiceRole;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;

@RestController
@RequestMapping("/v1/layouts")
public class LayoutController {

  private static final Logger LOGGER = LoggerFactory.getLogger(LayoutController.class);

  private final LayoutServiceRole layoutService;
  private final IModelAccessFacade modelAccess;
  private final VelocityManager velocityManager;
  private final ModelContext context;
  private final Execution execution;

  @Inject
  public LayoutController(LayoutServiceRole layoutService, IModelAccessFacade modelAccess,
      VelocityManager velocityManager, ModelContext context, Execution execution) {
    this.layoutService = layoutService;
    this.modelAccess = modelAccess;
    this.velocityManager = velocityManager;
    this.context = context;
    this.execution = execution;
  }

  @CrossOrigin(origins = "*")
  @GetMapping(value = "/json/{layoutSpaceName}", produces = MediaType.APPLICATION_JSON_VALUE)
  public String renderLayoutAsJson(@PathVariable("layoutSpaceName") String layoutSpaceName) {
    SpaceReference layoutRef = RefBuilder.from(context.getWikiRef()).space(layoutSpaceName)
        .build(SpaceReference.class);
    if (!layoutService.existsLayout(layoutRef)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layout not found");
    }
    return layoutService.renderLayoutAsJson(layoutRef);
  }

  @CrossOrigin(origins = "*")
  @GetMapping(
      value = "/partial",
      produces = MediaType.APPLICATION_XML_VALUE)
  String renderLayoutPartial(RenderPartialRequest renderPartialRequest) {
    LOGGER.info("GET ModelAttribute renderLayoutPartial: {}", renderPartialRequest);
    var contextDocRef = buildDocRef(renderPartialRequest.getContextDocSpace(),
        renderPartialRequest.getContextDocName());
    var layoutNodeRef = buildDocRef(renderPartialRequest.getLayoutSpace(),
        renderPartialRequest.getStartNodeName());
    return modelAccess.getDocumentOpt(contextDocRef)
        .flatMap((XWikiDocument contextDoc) -> {
          initialiseContext(contextDoc, renderPartialRequest.getLanguage());
          return layoutService.renderLayoutPartial(layoutNodeRef);
        }).orElse("");
  }

  private void initialiseContext(XWikiDocument contextDoc, String language) {
    var eContext = execution.getContext();
    var xContext = context.getXWikiContext();
    var vContext = velocityManager.getVelocityContext();
    eContext.set(XWikiExecutionProp.DOC, contextDoc);
    xContext.setDoc(contextDoc);
    vContext.put("doc", new Document(contextDoc, context.getXWikiContext()));
    xContext.setLanguage(language);
    vContext.put("language", language);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
    LOGGER.info("Bad Request", e);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleException(Exception e) {
    LOGGER.warn("Internal Server error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
  }

  private DocumentReference buildDocRef(String space, String docName) {
    return RefBuilder.from(context.getWikiRef()).space(space)
        .doc(Objects.requireNonNull(Strings.emptyToNull(docName)))
        .build(DocumentReference.class);
  }

}
