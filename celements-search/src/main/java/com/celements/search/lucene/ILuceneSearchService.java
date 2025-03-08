package com.celements.search.lucene;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.util.Version;
import org.xwiki.component.annotation.ComponentRole;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;

import com.celements.model.access.exception.DocumentLoadException;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.classes.fields.ref.ReferenceField;
import com.celements.search.lucene.query.IQueryRestriction;
import com.celements.search.lucene.query.LuceneDocType;
import com.celements.search.lucene.query.LuceneQuery;
import com.celements.search.lucene.query.QueryRestriction;
import com.celements.search.lucene.query.QueryRestrictionGroup;
import com.celements.search.lucene.query.QueryRestrictionGroup.Type;
import com.celements.search.lucene.query.QueryRestrictionString;
import com.google.common.collect.Range;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.lucene.IndexFields;

@ComponentRole
public interface ILuceneSearchService {

  /**
   * @deprecated NOT THREAD SAFE! instead use {@link #getDateFormatter()}
   */
  @Deprecated
  DateFormat SDF = new SimpleDateFormat("yyyyMMddHHmm");
  String DATE_LOW = IndexFields.DATE_LOW;
  String DATE_HIGH = IndexFields.DATE_HIGH;

  Version getVersion();

  /**
   * @return LuceneQuery object for current wiki with {@link LuceneDocType#DOC} only
   */
  LuceneQuery createQuery();

  /**
   * @deprecated instead use {@link #createQuery()} with
   *             {@link LuceneQuery#setDocTypes(java.util.Collection)}
   * @param types
   *          e.g. 'wikipage' or 'attachment'
   */
  @Deprecated
  LuceneQuery createQuery(List<String> types);

  /**
   * @deprecated since 4.3 instead use {@link #getDateFormatter()}
   */
  @Deprecated
  DateFormat getSDF();

  DateTimeFormatter getDateFormatter();

  QueryRestrictionGroup createRestrictionGroup(Type type);

  QueryRestrictionGroup createRestrictionGroup(Type type, List<String> fields,
      List<String> values);

  QueryRestrictionGroup createRestrictionGroup(Type type, List<String> fields,
      List<String> values, boolean tokenize, boolean fuzzy);

  QueryRestrictionString createRestriction(String query) throws ParseException;

  QueryRestriction createRestriction(String field, String value);

  /**
   * @deprecated since 6.0 instead use {@link QueryRestriction#setMode()}
   */
  @Deprecated
  QueryRestriction createRestriction(String field, String value, boolean tokenize);

  /**
   * @deprecated since 6.0 instead use {@link QueryRestriction#setFuzzy()}
   */
  @Deprecated
  QueryRestriction createRestriction(String field, String value, boolean tokenize,
      boolean fuzzy);

  QueryRestriction createDocTypeRestriction(LuceneDocType docType);

  QueryRestriction createSpaceRestriction(SpaceReference spaceRef);

  QueryRestriction createDocRestriction(DocumentReference docRef);

  /**
   * @deprecated instead use {@link #createObjectRestriction(ClassReference)}
   */
  @Deprecated
  QueryRestriction createObjectRestriction(DocumentReference classRef);

  QueryRestriction createObjectRestriction(ClassReference classRef);

  /**
   * @deprecated instead use {@link #createRestriction(ClassField, Object)}
   */
  @Deprecated
  QueryRestriction createFieldRestriction(DocumentReference classRef, String field,
      String value);

  <T> QueryRestriction createRestriction(ClassField<T> field, T value);

  /**
   * @deprecated instead use {@link #createRestriction(ClassField, Object, boolean)}
   */
  @Deprecated
  QueryRestriction createFieldRestriction(DocumentReference classRef, String field,
      String value, boolean tokenize);

  <T> QueryRestriction createRestriction(ClassField<T> field, T value, boolean tokenize);

  /**
   * @deprecated instead use {@link #createRestriction(ClassField, Object)} with a
   *             {@link ReferenceField}
   */
  @Deprecated
  IQueryRestriction createFieldRefRestriction(DocumentReference classRef, String field,
      EntityReference ref);

  /**
   * @deprecated since 6.0 instead use {@link #createRangeRestriction(String, Range)}
   */
  @Deprecated
  QueryRestriction createRangeRestriction(String field, String from, String to);

  /**
   * @deprecated since 6.0 instead use {@link #createRangeRestriction(String, Range)}
   */
  @Deprecated
  QueryRestriction createRangeRestriction(String field, String from, String to,
      boolean inclusive);

  @NotNull
  QueryRestriction createRangeRestriction(@Nullable String field,
      @NotNull Range<String> range);

  /**
   * @deprecated since 6.0 instead use {@link #createDateRestriction(String, LocalDateTime)}
   */
  @Deprecated
  QueryRestriction createDateRestriction(String field, Date date);

  @NotNull
  QueryRestriction createDateRestriction(@Nullable String field,
      @NotNull LocalDateTime date);

  /**
   * @deprecated since 6.0 instead use {@link #createDateRangeRestriction(String, Range)}
   */
  @Deprecated
  QueryRestriction createFromDateRestriction(String field, Date fromDate, boolean inclusive);

  /**
   * @deprecated since 6.0 instead use {@link #createDateRangeRestriction(String, Range)}
   */
  @Deprecated
  QueryRestriction createToDateRestriction(String field, Date toDate, boolean inclusive);

  /**
   * @deprecated since 6.0 instead use {@link #createDateRangeRestriction(String, Range)}
   */
  @Deprecated
  QueryRestriction createFromToDateRestriction(String field, Date fromDate, Date toDate,
      boolean inclusive);

  @NotNull
  QueryRestriction createDateRangeRestriction(@Nullable String field,
      @NotNull Range<LocalDateTime> range);

  @NotNull
  QueryRestrictionGroup createDateRangeRestriction(
      @Nullable String startField, @Nullable String endField,
      @NotNull Range<LocalDateTime> range);

  QueryRestriction createNumberRestriction(String field, Number number);

  /**
   * @deprecated since 6.0 instead use {@link #createNumberRangeRestriction(String, Range)}
   */
  @Deprecated
  QueryRestriction createFromToNumberRestriction(String field, Number fromNumber,
      Number toNumber, boolean inclusive);

  @NotNull
  QueryRestriction createNumberRangeRestriction(@Nullable String field,
      @NotNull Range<? extends Number> range);

  QueryRestrictionGroup createAttachmentRestrictionGroup(List<String> mimeTypes,
      List<String> mimeTypesBlackList, List<String> filenamePrefs);

  LuceneSearchResult search(LuceneQuery query);

  LuceneSearchResult search(LuceneQuery query, List<String> sortFields,
      List<String> languages);

  LuceneSearchResult searchWithoutChecks(LuceneQuery query);

  LuceneSearchResult searchWithoutChecks(LuceneQuery query, List<String> sortFields,
      List<String> languages);

  LuceneSearchResult search(String queryString, List<String> sortFields,
      List<String> languages);

  LuceneSearchResult searchWithoutChecks(String queryString, List<String> sortFields,
      List<String> languages);

  int getResultLimit();

  int getResultLimit(boolean skipChecks);

  /**
   * @deprecated instead use {@link ILuceneIndexService}
   */
  @Deprecated
  void queueForIndexing(DocumentReference docRef) throws DocumentLoadException,
      DocumentNotExistsException;

  /**
   * @deprecated instead use {@link ILuceneIndexService}
   */
  @Deprecated
  void queueForIndexing(XWikiDocument doc);

}
