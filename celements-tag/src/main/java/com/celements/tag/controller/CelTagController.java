package com.celements.tag.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.spring.security.AuthenticatedBaseController;
import com.celements.tag.CelTag;
import com.celements.tag.CelTagService;
import com.celements.web.service.IWebUtilsService;
import com.fasterxml.jackson.annotation.JsonInclude;

import one.util.streamex.StreamEx;

@RestController
@RequestMapping("/v1/celtags")
@PreAuthorize("permitAll()")
public class CelTagController extends AuthenticatedBaseController {

  private final CelTagService tagService;
  private final ModelContext context;
  private final ModelUtils modelUtils;
  private final IWebUtilsService webUtils;

  @Inject
  public CelTagController(
      CelTagService tagService,
      ModelContext context,
      ModelUtils modelUtils,
      IWebUtilsService webUtils) {
    this.tagService = tagService;
    this.context = context;
    this.modelUtils = modelUtils;
    this.webUtils = webUtils;
  }

  @GetMapping
  public Map<String, List<TagDto>> getTags() {
    return tagService.streamAllTags()
        .filter(this::isInCurrentScope)
        .filter(CelTag::isRoot)
        .mapToEntry(CelTag::getType, TagDto::new)
        .grouping();
  }

  @GetMapping("/types")
  public Set<String> getTypes() {
    return tagService.streamAllTags()
        .filter(this::isInCurrentScope)
        .map(CelTag::getType)
        .toSet();
  }

  @GetMapping("/{type}")
  public List<TagDto> getTagsByType(
      @PathVariable String type) {
    return tagService.streamTags(type)
        .filter(this::isInCurrentScope)
        .filter(CelTag::isRoot)
        .sorted(CelTag.CMP_ORDER)
        .map(TagDto::new)
        .toList();
  }

  @GetMapping("/{type}/{name}")
  public ResponseEntity<TagDto> getTagByName(
      @PathVariable String type,
      @PathVariable String name) {
    return tagService.streamTags(type)
        .filter(this::isInCurrentScope)
        .filter(tag -> tag.getName().equals(name))
        .findFirst()
        .map(TagDto::new)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private boolean isInCurrentScope(CelTag tag) {
    return modelUtils.isMainWiki(context.getWikiRef()) || tag.hasScope(context.getWikiRef());
  }

  @Immutable
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public class TagDto {

    public final String name;
    public final String scope;
    public final int order;
    public final Map<String, String> prettyName;
    public final List<TagDto> children;

    public TagDto(CelTag tag) {
      name = tag.getName();
      scope = tag.getScope().map(modelUtils::serializeRef).orElse(null);
      order = tag.getOrder();
      prettyName = StreamEx.of(webUtils.getAllowedLanguages())
          .mapToEntry(tag::getPrettyName)
          .flatMapValues(Optional::stream)
          .toImmutableMap();
      children = tag.getChildren()
          .filter(CelTagController.this::isInCurrentScope)
          .sorted(CelTag.CMP_ORDER)
          .map(TagDto::new)
          .toImmutableList();
    }
  }

}
