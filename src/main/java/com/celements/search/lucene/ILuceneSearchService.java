package com.celements.search.lucene;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.util.Version;
import org.xwiki.component.annotation.ComponentRole;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;

import com.celements.model.access.exception.DocumentLoadException;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.search.lucene.query.IQueryRestriction;
import com.celements.search.lucene.query.LuceneDocType;
import com.celements.search.lucene.query.LuceneQuery;
import com.celements.search.lucene.query.QueryRestriction;
import com.celements.search.lucene.query.QueryRestrictionGroup;
import com.celements.search.lucene.query.QueryRestrictionGroup.Type;
import com.celements.search.lucene.query.QueryRestrictionString;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.lucene.IndexFields;

@ComponentRole
public interface ILuceneSearchService {

  /**
   * @deprecated NOT THREAD SAFE! instead use getSDF()
   */
  @Deprecated
  public static final DateFormat SDF = new SimpleDateFormat("yyyyMMddHHmm");
  public static final String DATE_LOW = IndexFields.DATE_LOW;
  public static final String DATE_HIGH = IndexFields.DATE_HIGH;

  public Version getVersion();

  /**
   * @return LuceneQuery object with type 'wikipage' only
   */
  public LuceneQuery createQuery();

  /**
   * @param types
   *          e.g. 'wikipage' or 'attachment'
   */
  public LuceneQuery createQuery(List<String> types);

  public DateFormat getSDF();

  public QueryRestrictionGroup createRestrictionGroup(Type type);

  public QueryRestrictionGroup createRestrictionGroup(Type type, List<String> fields,
      List<String> values);

  public QueryRestrictionGroup createRestrictionGroup(Type type, List<String> fields,
      List<String> values, boolean tokenize, boolean fuzzy);

  public QueryRestrictionString createRestriction(String query) throws ParseException;

  public QueryRestriction createRestriction(String field, String value);

  public QueryRestriction createRestriction(String field, String value, boolean tokenize);

  public QueryRestriction createRestriction(String field, String value, boolean tokenize,
      boolean fuzzy);

  public QueryRestriction createDocTypeRestriction(LuceneDocType docType);

  public QueryRestriction createSpaceRestriction(SpaceReference spaceRef);

  public QueryRestriction createDocRestriction(DocumentReference docRef);

  public QueryRestriction createObjectRestriction(DocumentReference classRef);

  public QueryRestriction createFieldRestriction(DocumentReference classRef, String field,
      String value);

  public QueryRestriction createFieldRestriction(DocumentReference classRef, String field,
      String value, boolean tokenize);

  /**
   * Creates a restriction for a class field which has a reference set as value.<br>
   * NOTE: classRef must contain the same wikiRef as query
   *
   * @param classRef
   * @param field
   * @param ref
   * @return
   */
  public IQueryRestriction createFieldRefRestriction(DocumentReference classRef, String field,
      EntityReference ref);

  public QueryRestriction createRangeRestriction(String field, String from, String to);

  public QueryRestriction createRangeRestriction(String field, String from, String to,
      boolean inclusive);

  public QueryRestriction createDateRestriction(String field, Date date);

  public QueryRestriction createFromDateRestriction(String field, Date fromDate, boolean inclusive);

  public QueryRestriction createToDateRestriction(String field, Date toDate, boolean inclusive);

  public QueryRestriction createFromToDateRestriction(String field, Date fromDate, Date toDate,
      boolean inclusive);

  public QueryRestriction createNumberRestriction(String field, Number number);

  public QueryRestriction createFromToNumberRestriction(String field, Number fromNumber,
      Number toNumber, boolean inclusive);

  public QueryRestrictionGroup createAttachmentRestrictionGroup(List<String> mimeTypes,
      List<String> mimeTypesBlackList, List<String> filenamePrefs);

  public LuceneSearchResult search(LuceneQuery query, List<String> sortFields,
      List<String> languages);

  public LuceneSearchResult searchWithoutChecks(LuceneQuery query, List<String> sortFields,
      List<String> languages);

  public LuceneSearchResult search(String queryString, List<String> sortFields,
      List<String> languages);

  public LuceneSearchResult searchWithoutChecks(String queryString, List<String> sortFields,
      List<String> languages);

  public int getResultLimit();

  public int getResultLimit(boolean skipChecks);

  /**
   * @deprecated instead use {@link ILuceneIndexService}
   */
  @Deprecated
  public void queueForIndexing(DocumentReference docRef) throws DocumentLoadException,
      DocumentNotExistsException;

  /**
   * @deprecated instead use {@link ILuceneIndexService}
   */
  @Deprecated
  public void queueForIndexing(XWikiDocument doc);

}
