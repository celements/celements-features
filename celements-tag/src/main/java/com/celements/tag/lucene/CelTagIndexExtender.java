package com.celements.tag.lucene;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.celements.model.access.IModelAccessFacade;
import com.celements.tag.CelTag;
import com.celements.tag.CelTagService;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.lucene.AbstractIndexData;
import com.xpn.xwiki.plugin.lucene.DocumentData;
import com.xpn.xwiki.plugin.lucene.indexExtension.ILuceneIndexExtender;
import com.xpn.xwiki.plugin.lucene.indexExtension.IndexExtensionField;
import com.xpn.xwiki.plugin.lucene.indexExtension.IndexExtensionField.ExtensionType;

@Component
public class CelTagIndexExtender implements ILuceneIndexExtender {

  private static final String PREFIX = "celtags";
  public static final Function<CelTag, String> INDEX_FIELD = t -> PREFIX + "_" + t.getType();

  private final CelTagService tagService;
  private final IModelAccessFacade modelAccess;

  @Inject
  public CelTagIndexExtender(CelTagService tagService, IModelAccessFacade modelAccess) {
    this.tagService = tagService;
    this.modelAccess = modelAccess;
  }

  @Override
  public String getName() {
    return PREFIX;
  }

  @Override
  public boolean isEligibleIndexData(AbstractIndexData data) {
    return data instanceof DocumentData;
  }

  @Override
  public Collection<IndexExtensionField> getExtensionFields(AbstractIndexData data) {
    DocumentData docData = (DocumentData) data;
    XWikiDocument doc = modelAccess.getOrCreateDocument(docData.getDocumentReference());
    return toExtensionFields(tagService.getDocTags(doc))
        .collect(Collectors.toList());
  }

  public Stream<IndexExtensionField> toExtensionFields(Stream<CelTag> tags) {
    return tags.flatMap(CelTag::getThisAndAncestors)
        .distinct()
        .map(tag -> new IndexExtensionField.Builder(INDEX_FIELD.apply(tag))
            .extensionType(ExtensionType.ADD)
            .value(tag.getName())
            .build());
  }

}
