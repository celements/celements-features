package com.celements.search.web.packages;

import static com.celements.search.lucene.query.QueryRestriction.QueryMode.*;
import static com.celements.search.web.classes.WebSearchFieldConfigClass.*;
import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.model.field.FieldAccessor;
import com.celements.model.field.XObjectFieldAccessor;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.search.lucene.ILuceneSearchService;
import com.celements.search.lucene.query.IQueryRestriction;
import com.celements.search.lucene.query.LuceneDocType;
import com.celements.search.lucene.query.QueryRestriction.QueryMode;
import com.celements.search.lucene.query.QueryRestrictionGroup;
import com.celements.search.lucene.query.QueryRestrictionGroup.Type;
import com.celements.search.web.classes.WebSearchFieldConfigClass;
import com.celements.velocity.VelocityService;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import one.util.streamex.EntryStream;

@ThreadSafe
@Singleton
@Component(FieldWebSearchPackage.NAME)
public class FieldWebSearchPackage implements WebSearchPackage {

  private static final Logger LOGGER = LoggerFactory.getLogger(FieldWebSearchPackage.class);

  public static final String NAME = "field";

  @Requirement
  private ILuceneSearchService searchService;

  @Requirement(XObjectFieldAccessor.NAME)
  private FieldAccessor<BaseObject> xFieldAccess;

  @Requirement
  private VelocityService velocityService;

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean isDefault() {
    return false;
  }

  @Override
  public boolean isRequired(XWikiDocument cfgDoc) {
    return getConfigObjFetcher(cfgDoc).exists();
  }

  @Override
  public LuceneDocType getDocType() {
    return LuceneDocType.DOC;
  }

  @Override
  public IQueryRestriction getQueryRestriction(XWikiDocument cfgDoc, String searchTerm) {
    Map<Type, List<BaseObject>> groupedObjs = getConfigObjFetcher(cfgDoc).stream().collect(
        groupingBy(obj -> xFieldAccess.get(obj, FIELD_OPERATOR).orElse(Type.OR)));
    return createRestrictionGroup(Type.AND, EntryStream.of(groupedObjs)
        .mapValues(objs -> objs.stream().map(obj -> asRestriction(obj, searchTerm)))
        .mapKeyValue(this::createRestrictionGroup));
  }

  private QueryRestrictionGroup createRestrictionGroup(Type type,
      Stream<? extends IQueryRestriction> restr) {
    QueryRestrictionGroup grp = searchService.createRestrictionGroup(type);
    restr.forEach(grp::add);
    return grp;
  }

  private IQueryRestriction asRestriction(BaseObject obj, String searchTerm) {
    String field = xFieldAccess.get(obj, FIELD_NAME).orElse("");
    String value = xFieldAccess.get(obj, FIELD_VALUE)
        .map(this::evaluateVelocityText).orElse(searchTerm);
    float boost = xFieldAccess.get(obj, FIELD_BOOST).orElse(1f);
    return createRestrictionGroup(Type.OR, xFieldAccess.get(obj, FIELD_SEARCH_MODE)
        .orElseGet(() -> List.of(TOKENIZED, EXACT))
        .stream()
        .map(mode -> createRestriction(mode, field, value, boost)));
  }

  private IQueryRestriction createRestriction(QueryMode mode,
      String field, String value, float boost) {
    return searchService.createRestriction(field, value)
        .setMode(mode)
        .setBoost(boost * (mode == EXACT ? 2 : 1));
  }

  private String evaluateVelocityText(String text) {
    try {
      return velocityService.evaluateVelocityText(text);
    } catch (XWikiVelocityException exc) {
      LOGGER.info("evaluateVelocityText: failed", exc);
      return text;
    }
  }

  @Override
  public com.google.common.base.Optional<ClassReference> getLinkedClassRef() {
    return com.google.common.base.Optional.absent();
  }

  private XWikiObjectFetcher getConfigObjFetcher(XWikiDocument cfgDoc) {
    return (cfgDoc != null) ? XWikiObjectFetcher.on(cfgDoc)
        .filter(WebSearchFieldConfigClass.CLASS_REF)
        : XWikiObjectFetcher.empty();
  }

}
