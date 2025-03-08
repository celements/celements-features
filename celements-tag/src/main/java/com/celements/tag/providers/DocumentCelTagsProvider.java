package com.celements.tag.providers;

import static com.celements.common.MoreOptional.*;
import static com.celements.common.lambda.LambdaExceptionUtil.*;
import static com.celements.navigation.INavigationClassConfig.*;
import static com.google.common.base.Strings.*;
import static java.util.function.Predicate.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.reference.RefBuilder;
import com.celements.model.util.ModelUtils;
import com.celements.pagetype.classes.PageTypeClass;
import com.celements.tag.CelTag;
import com.celements.tag.CelTagPageType;
import com.celements.tag.classdefs.CelTagDependencyClass;
import com.celements.web.CelConstant;
import com.celements.wiki.WikiService;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import one.util.streamex.StreamEx;

@Component
public class DocumentCelTagsProvider implements CelTagsProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentCelTagsProvider.class);

  private static final String XWQL_TAGS = "from "
      + "doc.object(" + PageTypeClass.CLASS_REF.serialize() + ") pt "
      + "where doc.translation = 0 "
      + "and pt." + PageTypeClass.FIELD_PAGE_TYPE.getName() + " = '" + CelTagPageType.NAME + "'";

  private final WikiService wikiService;
  private final QueryManager queryManager;
  private final IModelAccessFacade modelAccess;
  private final ModelUtils modelUtils;

  @Inject
  public DocumentCelTagsProvider(
      QueryManager queryManager,
      @Lazy WikiService wikiService,
      IModelAccessFacade modelAccess,
      ModelUtils modelUtils) {
    this.wikiService = wikiService;
    this.queryManager = queryManager;
    this.modelAccess = modelAccess;
    this.modelUtils = modelUtils;
  }

  @Override
  public Collection<CelTag.Builder> get() throws CelTagsProvisionException {
    try {
      var tags = wikiService.streamAllWikis()
          .flatMap(rethrow(this::loadTagDocs))
          .map(modelAccess::getOrCreateDocument)
          .map(this::asCelTagBuilder)
          .collect(Collectors.toList());
      LOGGER.info("providing tags: {}", tags);
      return tags;
    } catch (QueryException exc) {
      throw new CelTagsProvisionException(exc);
    }
  }

  private Stream<DocumentReference> loadTagDocs(WikiReference wiki) throws QueryException {
    List<String> results = queryManager.createQuery(XWQL_TAGS, Query.XWQL)
        .setWiki(wiki.getName())
        .<String>execute();
    LOGGER.debug("loaded tags for {}: {}", wiki, results);
    return results.stream().map(fn -> modelUtils.resolveRef(fn, DocumentReference.class, wiki));
  }

  private CelTag.Builder asCelTagBuilder(XWikiDocument tagDefDoc) {
    final var tagDefDocRef = tagDefDoc.getDocRef();
    final var type = getTagType(tagDefDoc);
    var builder = new CelTag.Builder();
    builder.source(tagDefDocRef);
    builder.type(type);
    builder.name(getTagName(tagDefDoc));
    if (!CelConstant.CENTRAL_WIKI.equals(tagDefDoc.getWikiRef())) {
      builder.scope(tagDefDoc.getWikiRef());
    }
    Optional.ofNullable(tagDefDoc.getParentReference())
        .map(modelAccess::getOrCreateDocument)
        .filter(doc -> type.equals(getTagType(doc)))
        .map(this::getTagName)
        .ifPresent(builder::parent);
    XWikiObjectFetcher.on(tagDefDoc)
        .fetchField(CelTagDependencyClass.FIELD_REFERENCE)
        .stream()
        .map(modelAccess::getOrCreateDocument)
        .forEach(doc -> builder.expectDependency(getTagType(doc), getTagName(doc)));
    builder.prettyName(lang -> getPrettyName(tagDefDocRef, lang));
    builder.order(getMenuItemOrder(tagDefDoc));
    return builder;
  }

  /**
   * space title (WebPreferences) or space name
   */
  private String getTagType(XWikiDocument tagDefDoc) {
    var tagSpaceRef = tagDefDoc.getDocRef().getLastSpaceReference();
    return modelAccess.getDocumentOpt(RefBuilder.from(tagSpaceRef)
        .doc(XWikiConstant.WEB_PREF_DOC_NAME)
        .build(DocumentReference.class))
        .flatMap(this::getSanitisedDocTitle)
        .orElse(tagSpaceRef.getName());
  }

  private String getTagName(XWikiDocument doc) {
    return getSanitisedDocTitle(doc).orElse(doc.getDocRef().getName());
  }

  private Optional<String> getSanitisedDocTitle(XWikiDocument doc) {
    return Optional.ofNullable(emptyToNull(doc.getTitle().trim()
        .replaceAll("[^!-~]", ""))); // printable ASCII only
  }

  private Optional<String> getPrettyName(DocumentReference tagDefDocRef, String lang) {
    XWikiDocument tagDefDoc = modelAccess.getOrCreateDocument(tagDefDocRef);
    return streamMenuNameObjs(tagDefDoc, lang)
        .map(obj -> obj.getStringValue(MENU_NAME_FIELD))
        .filter(not(String::isBlank))
        .findFirst()
        .or(() -> asNonBlank(tagDefDoc.getTitle()))
        .or(() -> Optional.of(tagDefDocRef.getName()));
  }

  private StreamEx<BaseObject> streamMenuNameObjs(XWikiDocument tagDefDoc, String lang) {
    Set<String> validLangs = Set.of(nullToEmpty(lang).trim(), "");
    return StreamEx.of(XWikiObjectFetcher.on(tagDefDoc)
        .filter(new ClassReference(MENU_NAME_CLASS_SPACE, MENU_NAME_CLASS_DOC))
        .stream())
        .mapToEntry(obj -> obj.getStringValue(MENU_NAME_LANG_FIELD), obj -> obj)
        .filterKeys(validLangs::contains)
        .reverseSorted(Map.Entry.comparingByKey()) // specific before default lang
        .values();
  }

  private Integer getMenuItemOrder(XWikiDocument tagDefDoc) {
    return XWikiObjectFetcher.on(tagDefDoc)
        .filter(new ClassReference(MENU_ITEM_CLASS_SPACE, MENU_ITEM_CLASS_DOC))
        .findFirst()
        .map(obj -> obj.getIntValue(MENU_POSITION_FIELD, 0))
        .orElse(0);
  }

}
