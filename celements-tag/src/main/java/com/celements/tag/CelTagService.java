package com.celements.tag;

import static com.celements.common.lambda.LambdaExceptionUtil.*;
import static com.google.common.base.Strings.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import com.celements.common.lambda.Try;
import com.celements.model.field.XObjectFieldAccessor;
import com.celements.model.object.xwiki.XWikiObjectEditor;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.tag.classdefs.CelTagClass;
import com.celements.tag.providers.CelTagsProvider;
import com.celements.tag.providers.CelTagsProvider.CelTagsProvisionException;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.util.AbstractXWikiRunnable;

import one.util.streamex.StreamEx;

@Service
public class CelTagService implements ApplicationListener<CelTagService.RefreshEvent> {

  private static final Logger LOGGER = LoggerFactory.getLogger(CelTagService.class);

  private final ListableBeanFactory beanFactory;
  private final XObjectFieldAccessor fieldAccessor;
  private final AtomicReference<Try<Multimap<String, CelTag>, CelTagsProvisionException>> cache;

  @Inject
  public CelTagService(
      ListableBeanFactory beanFactory,
      XObjectFieldAccessor fieldAccessor) {
    this.beanFactory = beanFactory;
    this.fieldAccessor = fieldAccessor;
    this.cache = new AtomicReference<>();
  }

  @NotNull
  public Optional<CelTag> getTag(@Nullable String type, @Nullable String name) {
    return getTagsByType().get(type).stream()
        .filter(tag -> tag.getName().equals(name))
        .findFirst();
  }

  @NotNull
  public StreamEx<CelTag> streamTags(@Nullable String type) {
    return StreamEx.of(getTagsByType().get(nullToEmpty(type).toLowerCase()));
  }

  @NotNull
  public StreamEx<CelTag> streamAllTags() {
    return StreamEx.of(getTagsByType().values());
  }

  @NotNull
  public Multimap<String, CelTag> getTagsByType() {
    try {
      return cache.updateAndGet(trying -> (trying != null) && trying.isSuccess()
          ? trying
          : Try.to(this::collectAllTags))
          .getOrThrow();
    } catch (CelTagsProvisionException exc) {
      LOGGER.error("getTagsByType - failed", exc);
    }
    return ImmutableMultimap.of();
  }

  private void refresh() {
    CompletableFuture.runAsync(new AbstractXWikiRunnable() {

      @Override
      protected void runInternal() {
        cache.set(Try.to(CelTagService.this::collectAllTags));
      }
    });
  }

  private Multimap<String, CelTag> collectAllTags() throws CelTagsProvisionException {
    List<CelTag.Builder> tagBuilders = beanFactory
        .getBeansOfType(CelTagsProvider.class)
        .values().stream()
        .map(rethrow(CelTagsProvider::get))
        .flatMap(Collection::stream)
        .collect(toList());
    LOGGER.info("collectAllTags - {}", tagBuilders);
    return topologicalBuild(tagBuilders);
  }

  /**
   * build tag graph in topological order with some form of Kahn's algorithm, assuming directed
   * acyclic graph
   */
  private Multimap<String, CelTag> topologicalBuild(List<CelTag.Builder> toBuild) {
    var mapBuilder = ImmutableMultimap.<String, CelTag>builder();
    while (!toBuild.isEmpty()) {
      List<CelTag> builtTags = buildTagsWithAllDependencies(toBuild.iterator());
      for (CelTag tag : builtTags) {
        mapBuilder.put(tag.getType(), tag);
        toBuild.stream().forEach(b -> b.addDependency(tag));
      }
      if (builtTags.isEmpty()) {
        throw new IllegalStateException("tags don't form a directed acyclic graph: " + toBuild);
      }
    }
    return mapBuilder.build();
  }

  private List<CelTag> buildTagsWithAllDependencies(Iterator<CelTag.Builder> tagBuilderIter) {
    var built = new ArrayList<CelTag>();
    while (tagBuilderIter.hasNext()) {
      CelTag.Builder builder = tagBuilderIter.next();
      if (builder.hasAllDependencies()) {
        try {
          built.add(builder.build());
        } catch (IllegalArgumentException iae) {
          LOGGER.info("unable to build tag [{}]", builder, iae);
        }
        tagBuilderIter.remove();
      }
    }
    return built;
  }

  @NotNull
  public StreamEx<CelTag> getDocTags(@NotNull XWikiDocument doc) {
    return getDocTags(doc, null);
  }

  @NotNull
  public StreamEx<CelTag> getDocTags(@NotNull XWikiDocument doc, @Nullable String type) {
    XWikiObjectFetcher fetcher = XWikiObjectFetcher.on(doc)
        .filter(CelTagClass.CLASS_REF);
    type = nullToEmpty(type).trim();
    if (!type.isEmpty()) {
      fetcher = fetcher.filter(CelTagClass.FIELD_TYPE, type);
    }
    return StreamEx.of(fetcher.stream()).flatMap(obj -> getTags(
        fieldAccessor.get(obj, CelTagClass.FIELD_TYPE),
        fieldAccessor.get(obj, CelTagClass.FIELD_TAGS)
            .map(Set::copyOf).orElse(Set.of())));
  }

  private Stream<CelTag> getTags(Optional<String> type, Set<String> tags) {
    return type.map(getTagsByType()::get)
        .map(Collection::stream)
        .orElse(Stream.empty())
        .filter(tag -> tags.contains(tag.getName()));
  }

  @NotNull
  public boolean addTags(@NotNull XWikiDocument doc, @NotNull CelTag... tags) {
    boolean changed = false;
    for (var tagsByType : StreamEx.of(tags).groupingBy(CelTag::getType).entrySet()) {
      var editor = XWikiObjectEditor.on(doc)
          .filter(CelTagClass.FIELD_TYPE, tagsByType.getKey());
      editor.createFirstIfNotExists();
      changed |= editor.editField(CelTagClass.FIELD_TAGS)
          .all(() -> StreamEx.of(editor.fetch().fetchField(CelTagClass.FIELD_TAGS).stream())
              .flatMap(List::stream)
              .append(tagsByType.getValue().stream().map(CelTag::getName))
              .distinct()
              .toList());
    }
    return changed;
  }

  @Override
  public void onApplicationEvent(RefreshEvent event) {
    LOGGER.trace("onApplicationEvent - {}", event);
    refresh();
  }

  public static class RefreshEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    public RefreshEvent(Object source) {
      super(source);
    }

  }

}
