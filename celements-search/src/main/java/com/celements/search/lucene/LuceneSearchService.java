package com.celements.search.lucene;

import static com.celements.common.MoreObjectsCel.*;
import static com.celements.common.date.DateUtil.*;
import static com.celements.common.range.MoreRange.*;
import static com.celements.model.util.ReferenceSerializationMode.*;
import static com.celements.search.lucene.LuceneUtils.*;
import static com.google.common.base.MoreObjects.*;
import static java.util.stream.Collectors.*;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.common.date.DateFormat;
import com.celements.model.access.exception.DocumentLoadException;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.classes.fields.CustomClassField;
import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.search.lucene.index.analysis.CelAnalyzer;
import com.celements.search.lucene.query.IQueryRestriction;
import com.celements.search.lucene.query.LuceneDocType;
import com.celements.search.lucene.query.LuceneQuery;
import com.celements.search.lucene.query.QueryRestriction;
import com.celements.search.lucene.query.QueryRestrictionGroup;
import com.celements.search.lucene.query.QueryRestrictionGroup.Type;
import com.celements.search.lucene.query.QueryRestrictionString;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.lucene.IndexFields;
import com.xpn.xwiki.plugin.lucene.LucenePlugin;
import com.xpn.xwiki.plugin.lucene.indexExtension.IndexExtensionField;

@Component
public class LuceneSearchService implements ILuceneSearchService {

  // yyyyMMddHHmm
  public static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{12}|\\d{1,12}\\*)$");

  private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearchService.class);

  private static final boolean DEFAULT_TOKENIZE = true;
  private static final boolean DEFAULT_FUZZY = false;

  @Requirement
  private ILuceneIndexService luceneIndexService;

  @Requirement
  private ConfigurationSource cfgSrc;

  @Requirement
  private ModelUtils modelUtils;

  @Requirement
  private ModelContext context;

  @Override
  public java.text.DateFormat getSDF() {
    return new SimpleDateFormat("yyyyMMddHHmm");
  }

  @Override
  public DateTimeFormatter getDateFormatter() {
    return DateFormat.ofPattern("yyyyMMddHHmm");
  }

  @Override
  public Version getVersion() {
    Version defaultVersion = LucenePlugin.VERSION;
    try {
      return Version.valueOf(cfgSrc.getProperty("lucene.version", defaultVersion.toString()));
    } catch (IllegalArgumentException exc) {
      LOGGER.warn("invalid version defined", exc);
      return defaultVersion;
    }
  }

  @Override
  public LuceneQuery createQuery() {
    LuceneQuery query = new LuceneQuery();
    query.setDocTypes(List.of(LuceneDocType.DOC));
    query.setWiki(context.getWikiRef());
    return query;
  }

  @Override
  @Deprecated
  public LuceneQuery createQuery(List<String> types) {
    if ((types == null) || types.isEmpty()) {
      types = Arrays.asList(LucenePlugin.DOCTYPE_WIKIPAGE, LucenePlugin.DOCTYPE_ATTACHMENT);
    }
    return new LuceneQuery(types);
  }

  @Override
  public QueryRestrictionGroup createRestrictionGroup(Type type) {
    return new QueryRestrictionGroup(type);
  }

  @Override
  public QueryRestrictionGroup createRestrictionGroup(Type type, List<String> fields,
      List<String> values) {
    return createRestrictionGroup(type, fields, values, DEFAULT_TOKENIZE, DEFAULT_FUZZY);
  }

  @Override
  public QueryRestrictionGroup createRestrictionGroup(Type type, List<String> fields,
      List<String> values, boolean tokenize, boolean fuzzy) {
    QueryRestrictionGroup restrGrp = createRestrictionGroup(type);
    Iterator<String> fieldIter = getIter(fields);
    Iterator<String> valueIter = getIter(values);
    String field = null;
    String value = null;
    while (fieldIter.hasNext() || valueIter.hasNext()) {
      if (fieldIter.hasNext()) {
        field = fieldIter.next();
      }
      if (valueIter.hasNext()) {
        value = valueIter.next();
      }
      QueryRestriction restr = createRestriction(field, value, tokenize, fuzzy);
      if (restr != null) {
        restrGrp.add(restr);
      }
    }
    return restrGrp;
  }

  private Iterator<String> getIter(List<String> list) {
    if (list != null) {
      return list.iterator();
    } else {
      return Collections.<String>emptyList().iterator();
    }
  }

  @Override
  public QueryRestrictionString createRestriction(String query) throws ParseException {
    return new QueryRestrictionString(new QueryParser(getVersion(), "", new StandardAnalyzer(
        getVersion())).parse(query).toString());
  }

  @Override
  public QueryRestriction createRestriction(String field, String value) {
    QueryRestriction restriction = new QueryRestriction(field, value);
    if (!restriction.getSpecifier().isEmpty() && isAnalyzedOnIndex(restriction)) {
      getLucenePlugin().map(LucenePlugin::getAnalyzer)
          .flatMap(a -> tryCast(a, CelAnalyzer.class))
          .ifPresent(restriction::setAnalyzer);
    }
    LOGGER.trace("createRestriction - {}", restriction);
    return restriction;
  }

  private boolean isAnalyzedOnIndex(QueryRestriction restriction) {
    return new IndexExtensionField.Builder(restriction.getSpecifier())
        .value(restriction.getQuery())
        .build()
        .getLuceneField()
        .isTokenized();
  }

  @Override
  @Deprecated
  public QueryRestriction createRestriction(String field, String value, boolean tokenize) {
    return createRestriction(field, value).setTokenizeQuery(tokenize);
  }

  @Override
  @Deprecated
  public QueryRestriction createRestriction(String field, String value, boolean tokenize,
      boolean fuzzy) {
    QueryRestriction restriction = createRestriction(field, value, tokenize);
    return fuzzy ? restriction.setFuzzy() : restriction;
  }

  @Override
  public QueryRestriction createDocTypeRestriction(LuceneDocType docType) {
    return createRestriction(IndexFields.DOCUMENT_TYPE, exactify(docType.key));
  }

  @Override
  public QueryRestriction createSpaceRestriction(SpaceReference spaceRef) {
    return createRestriction(IndexFields.DOCUMENT_SPACE, exactify(spaceRef));
  }

  @Override
  public QueryRestriction createDocRestriction(DocumentReference docRef) {
    return createRestriction(IndexFields.DOCUMENT_FULLNAME, exactify(docRef));
  }

  @Override
  @Deprecated
  public QueryRestriction createObjectRestriction(DocumentReference classRef) {
    return createObjectRestriction(new ClassReference(classRef));
  }

  @Override
  public QueryRestriction createObjectRestriction(ClassReference classRef) {
    QueryRestriction restriction = null;
    if (classRef != null) {
      restriction = createRestriction(IndexFields.OBJECT, exactify(classRef));
    }
    return restriction;
  }

  @Override
  @Deprecated
  public QueryRestriction createFieldRestriction(DocumentReference classRef, String field,
      String value) {
    return createFieldRestriction(classRef, field, value, DEFAULT_TOKENIZE);
  }

  @Override
  public <T> QueryRestriction createRestriction(ClassField<T> field, T value) {
    return createRestriction(field, value, DEFAULT_TOKENIZE);
  }

  @Override
  @Deprecated
  public QueryRestriction createFieldRestriction(DocumentReference classRef, String field,
      String value, boolean tokenize) {
    QueryRestriction restriction = null;
    if ((classRef != null) && StringUtils.isNotBlank(field)) {
      restriction = createRestriction(asFieldName(classRef, field), value, tokenize);
    }
    return restriction;
  }

  @Override
  public <T> QueryRestriction createRestriction(ClassField<T> field, T value,
      boolean tokenize) {
    QueryRestriction restriction = null;
    if (field != null) {
      restriction = createRestriction(field.serialize(), serializeClassFieldValue(field, value),
          tokenize);
    }
    return restriction;
  }

  private <T> String serializeClassFieldValue(ClassField<T> field, T value) {
    Optional<?> ret;
    if (field instanceof CustomClassField) {
      ret = ((CustomClassField<T>) field).serialize(value);
    } else {
      ret = Optional.ofNullable(value);
    }
    return ret.map(Object::toString).orElse("");
  }

  @Override
  @Deprecated
  public IQueryRestriction createFieldRefRestriction(DocumentReference classRef, String field,
      EntityReference ref) {
    IQueryRestriction restriction = null;
    if ((classRef != null) && StringUtils.isNotBlank(field)) {
      String fieldStr = asFieldName(classRef, field);
      if (ref != null) {
        restriction = createRestriction(fieldStr, exactify(ref, GLOBAL));
        if (classRef.getWikiReference().equals(modelUtils.extractRef(ref,
            WikiReference.class).orNull())) {
          QueryRestrictionGroup restrGrp = createRestrictionGroup(Type.OR);
          restrGrp.add(restriction);
          restrGrp.add(createRestriction(fieldStr, exactify(ref, LOCAL)));
          restriction = restrGrp;
        }
      } else {
        restriction = createRestriction(fieldStr, "");
      }
    }
    return restriction;
  }

  @Override
  @Deprecated
  public QueryRestriction createRangeRestriction(String field, String from, String to) {
    return createRangeRestriction(field, from, to, true);
  }

  @Override
  @Deprecated
  public QueryRestriction createRangeRestriction(String field, String from, String to,
      boolean inclusive) {
    return createRangeRestriction(field, asRange(from, to,
        inclusive ? BoundType.CLOSED : BoundType.OPEN));
  }

  @Override
  public QueryRestriction createRangeRestriction(String field, Range<String> range) {
    BoundType lowerBoundType = range.hasLowerBound() ? range.lowerBoundType() : null;
    BoundType upperBoundType = range.hasUpperBound() ? range.upperBoundType() : null;
    if (LucenePlugin.VERSION.name().startsWith("LUCENE_3")) {
      // different bound types only supported with Lucene 4+
      lowerBoundType = Optional.ofNullable(lowerBoundType).orElse(upperBoundType);
      upperBoundType = lowerBoundType;
    }
    String value = ""
        + ((lowerBoundType == BoundType.OPEN) ? "{" : "[")
        + (range.hasLowerBound() ? range.lowerEndpoint() : "0")
        + " TO "
        + (range.hasUpperBound() ? range.upperEndpoint() : "zzzzzzzzzzzz")
        + ((upperBoundType == BoundType.OPEN) ? "}" : "]");
    return createRestriction(field, value, false).setAnalyzer(null);
  }

  @Override
  @Deprecated
  public QueryRestriction createDateRestriction(String field, Date date) {
    return createRestriction(field, IndexFields.dateToString(date), false).setAnalyzer(null);
  }

  @Override
  public QueryRestriction createDateRestriction(String field, LocalDateTime date) {
    return createRestriction(field, IndexFields.DATE_FORMATTER.apply(date), false)
        .setAnalyzer(null);
  }

  @Override
  @Deprecated
  public QueryRestriction createFromDateRestriction(String field, Date fromDate,
      boolean inclusive) {
    return createFromToDateRestriction(field, fromDate, null, inclusive);
  }

  @Override
  @Deprecated
  public QueryRestriction createToDateRestriction(String field, Date toDate, boolean inclusive) {
    return createFromToDateRestriction(field, null, toDate, inclusive);
  }

  @Override
  @Deprecated
  public QueryRestriction createFromToDateRestriction(String field, Date fromDate, Date toDate,
      boolean inclusive) {
    return createDateRangeRestriction(field, asRange(toDateTime(fromDate), toDateTime(toDate),
        inclusive ? BoundType.CLOSED : BoundType.OPEN));
  }

  private LocalDateTime toDateTime(Date date) {
    return (date != null) ? atZone(date.toInstant()).toLocalDateTime() : null;
  }

  @Override
  public QueryRestriction createDateRangeRestriction(String field, Range<LocalDateTime> range) {
    return createRangeRestriction(field, mapRange(range, IndexFields.DATE_FORMATTER));
  }

  @Override
  public QueryRestrictionGroup createDateRangeRestriction(String startField, String endField,
      Range<LocalDateTime> range) {
    QueryRestrictionGroup grp = createRestrictionGroup(Type.AND);
    grp.add(createDateRangeRestriction(endField, withoutUpperBound(range)));
    grp.add(createDateRangeRestriction(startField, withoutLowerBound(range)));
    return grp;
  }

  @Override
  public QueryRestriction createNumberRestriction(String field, Number number) {
    return createRestriction(field, IndexFields.numberToString(number), false).setAnalyzer(null);
  }

  @Override
  @Deprecated
  public QueryRestriction createFromToNumberRestriction(String field, Number fromNumber,
      Number toNumber, boolean inclusive) {
    String from = IndexFields.numberToString(firstNonNull(fromNumber, Integer.valueOf(0)));
    String to = IndexFields.numberToString(firstNonNull(toNumber, Integer.MAX_VALUE));
    return createRangeRestriction(field, from, to, inclusive);
  }

  @Override
  public QueryRestriction createNumberRangeRestriction(String field,
      Range<? extends Number> range) {
    return createRangeRestriction(field, mapRange(range, IndexFields::numberToString));
  }

  @Override
  public QueryRestrictionGroup createAttachmentRestrictionGroup(List<String> mimeTypes,
      List<String> mimeTypesBlackList, List<String> filenamePrefs) {
    mimeTypes = firstNonNull(mimeTypes, List.<String>of());
    mimeTypesBlackList = firstNonNull(mimeTypesBlackList, List.<String>of());
    filenamePrefs = firstNonNull(filenamePrefs, List.<String>of());
    QueryRestrictionGroup attGrp = createRestrictionGroup(Type.AND);
    attGrp.add(createRestrictionGroup(Type.OR, Arrays.asList(IndexFields.MIMETYPE),
        mimeTypes.stream().map(LuceneUtils::exactify).collect(toList())));
    attGrp.add(createRestrictionGroup(Type.OR, Arrays.asList(IndexFields.MIMETYPE),
        mimeTypesBlackList.stream().map(LuceneUtils::exactify).collect(toList())).setNegate(true));
    attGrp.add(createRestrictionGroup(Type.OR, Arrays.asList(IndexFields.FILENAME), filenamePrefs));
    return attGrp;
  }

  @Override
  public LuceneSearchResult search(LuceneQuery query) {
    return new LuceneSearchResult(query, null, null, false);
  }

  @Override
  public LuceneSearchResult search(LuceneQuery query, List<String> sortFields,
      List<String> languages) {
    return new LuceneSearchResult(query, sortFields, languages, false);
  }

  @Override
  public LuceneSearchResult searchWithoutChecks(LuceneQuery query) {
    return new LuceneSearchResult(query, null, null, true);
  }

  @Override
  public LuceneSearchResult searchWithoutChecks(LuceneQuery query, List<String> sortFields,
      List<String> languages) {
    return new LuceneSearchResult(query, sortFields, languages, true);
  }

  @Override
  public LuceneSearchResult search(String queryString, List<String> sortFields,
      List<String> languages) {
    return new LuceneSearchResult(queryString, sortFields, languages, false);
  }

  @Override
  public LuceneSearchResult searchWithoutChecks(String queryString, List<String> sortFields,
      List<String> languages) {
    return new LuceneSearchResult(queryString, sortFields, languages, true);
  }

  @Override
  public int getResultLimit() {
    return getResultLimit(false);
  }

  @Override
  public int getResultLimit(boolean skipChecks) {
    int limit = getLucenePlugin().map(p -> p.getResultLimit(skipChecks, getXContext())).orElse(0);
    LOGGER.debug("getResultLimit: got '{}' for skipChecks '{}'", limit, skipChecks);
    return limit;
  }

  @Deprecated
  @Override
  public void queueForIndexing(DocumentReference docRef) throws DocumentLoadException,
      DocumentNotExistsException {
    luceneIndexService.queueForIndexing(docRef);
  }

  @Deprecated
  @Override
  public void queueForIndexing(XWikiDocument doc) {
    luceneIndexService.queueForIndexing(doc);
  }

  private Optional<LucenePlugin> getLucenePlugin() {
    try {
      return Optional.of((LucenePlugin) getXContext().getWiki().getPlugin("lucene", getXContext()));
    } catch (NullPointerException npe) {
      LOGGER.warn("LucenePlugin not available, first request?");
      return Optional.empty();
    }
  }

  private XWikiContext getXContext() {
    return context.getXWikiContext();
  }

}
