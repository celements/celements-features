package com.celements.tag;

import static com.celements.tag.CelTag.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.context.ModelContext;
import com.celements.rights.access.EAccessLevel;
import com.celements.rights.access.IRightsAccessFacadeRole;

@Component("celtag")
public class CelTagScriptService implements ScriptService {

  private final CelTagService tagService;
  private final IRightsAccessFacadeRole rightsAccess;
  private final IModelAccessFacade modelAccess;
  private final ModelContext context;

  @Inject
  public CelTagScriptService(
      CelTagService tagService,
      IRightsAccessFacadeRole rightsAccess,
      IModelAccessFacade modelAccess,
      ModelContext context) {
    this.tagService = tagService;
    this.rightsAccess = rightsAccess;
    this.modelAccess = modelAccess;
    this.context = context;
  }

  public List<CelTag> getTags(String type) {
    return tagService.streamTags(type)
        .sorted(CMP_DEFAULT.apply(context.getLanguage().orElse("")))
        .filter(tag -> tag.hasScope(context.getWikiRef()))
        .collect(toList());
  }

  public CelTag getTag(String type, String name) {
    return tagService.getTag(type, name).orElse(null);
  }

  public List<CelTag> getTags(DocumentReference docRef) {
    return rightsAccess.hasAccessLevel(docRef, EAccessLevel.VIEW)
        ? tagService.getDocTags(modelAccess.getOrCreateDocument(docRef)).collect(toList())
        : new ArrayList<>();
  }

}
