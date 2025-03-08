package com.celements.tag.controller;

import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.celements.tag.CelTag;
import com.celements.tag.CelTagService;
import com.celements.web.service.IWebUtilsService;
import com.fasterxml.jackson.annotation.JsonInclude;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@RestController
@RequestMapping("/v1/celtags")
public class CelTagController {

  private final CelTagService tagService;
  private final IWebUtilsService webUtils;

  @Inject
  public CelTagController(
      CelTagService tagService,
      IWebUtilsService webUtils) {
    this.tagService = tagService;
    this.webUtils = webUtils;
  }

  @GetMapping
  public Map<String, List<TagDto>> getTags() {
    return EntryStream.of(tagService.getTagsByType().entries().stream())
        .filterValues(CelTag::isRoot)
        .mapValues(TagDto::new)
        .grouping();
  }

  @GetMapping("/types")
  public Set<String> getTypes() {
    return tagService.getTagsByType().keySet();
  }

  @GetMapping("/{type}")
  public List<TagDto> getTagsByType(
      @PathVariable String type) {
    return tagService.getTagsByType().get(type).stream()
        .filter(CelTag::isRoot)
        .sorted(CelTag.CMP_ORDER)
        .map(TagDto::new)
        .collect(toList());
  }

  @GetMapping("/{type}/{name}")
  public ResponseEntity<TagDto> getTagByName(
      @PathVariable String type,
      @PathVariable String name) {
    return tagService.getTag(type, name)
        .map(TagDto::new)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @Immutable
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public class TagDto {

    public final String name;
    public final int order;
    public final Map<String, String> prettyName;
    public final List<TagDto> children;

    public TagDto(CelTag tag) {
      name = tag.getName();
      order = tag.getOrder();
      prettyName = StreamEx.of(webUtils.getAllowedLanguages())
          .mapToEntry(tag::getPrettyName)
          .flatMapValues(Optional::stream)
          .toImmutableMap();
      children = tag.getChildren()
          .sorted(CelTag.CMP_ORDER)
          .map(TagDto::new)
          .toImmutableList();
    }
  }

}
