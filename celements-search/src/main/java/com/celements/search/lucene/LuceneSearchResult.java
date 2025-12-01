package com.celements.search.lucene;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryParser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.EntityReference;

import com.celements.model.context.ModelContext;
import com.celements.model.util.References;
import com.celements.search.lucene.query.LuceneQuery;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.plugin.lucene.LucenePlugin;
import com.xpn.xwiki.plugin.lucene.SearchResult;
import com.xpn.xwiki.plugin.lucene.SearchResults;
import com.xpn.xwiki.web.Utils;

public class LuceneSearchResult {

  private static final Logger LOGGER = LoggerFactory.getLogger(LuceneSearchResult.class);

  SearchResults searchResultsCache;
  LucenePlugin lucenePlugin;

  private final String queryString;
  private final List<String> sortFields;
  private final List<String> languages;
  private final boolean skipChecks;

  private int offset = 0;
  private int limit = -1;

  LuceneSearchResult(LuceneQuery query, List<String> sortFields, List<String> languages,
      boolean skipChecks) {
    this(query.getQueryString(), sortFields, languages, skipChecks);
  }

  LuceneSearchResult(String queryString, List<String> sortFields, List<String> languages,
      boolean skipChecks) {
    this.queryString = queryString;
    this.sortFields = getList(sortFields);
    this.languages = getList(languages);
    this.skipChecks = skipChecks;
  }

  private List<String> getList(List<String> list) {
    if (list != null) {
      return Collections.unmodifiableList(list);
    } else {
      return Collections.emptyList();
    }
  }

  public String getQueryString() {
    return queryString;
  }

  public List<String> getSortFields() {
    return sortFields;
  }

  String[] getSortFieldsArray() {
    return sortFields.toArray(new String[sortFields.size()]);
  }

  public List<String> getLanguages() {
    return languages;
  }

  String getLanguageString() {
    return StringUtils.join(languages, ",");
  }

  public boolean isSkipChecks() {
    return skipChecks;
  }

  public int getOffset() {
    return offset;
  }

  public LuceneSearchResult setOffset(int offset) {
    if (this.offset != offset) {
      this.offset = offset;
      // internal SearchResults.results.topDocs may only be called once with the same offset/limit
      searchResultsCache = null;
    }
    return this;
  }

  public int getLimit() {
    return limit;
  }

  public LuceneSearchResult setLimit(int limit) {
    if (this.limit != limit) {
      this.limit = limit;
      // internal SearchResults.results.topDocs may only be called once with the same offset/limit
      searchResultsCache = null;
    }
    return this;
  }

  public List<EntityReference> getResults(int offset, int limit) throws LuceneSearchException {
    return getResults(offset, limit, EntityReference.class);
  }

  public <T extends EntityReference> List<T> getResults(int offset, int limit, Class<T> token)
      throws LuceneSearchException {
    return this.setOffset(offset).setLimit(limit).getResults(token);
  }

  public List<EntityReference> getResults() throws LuceneSearchException {
    return getResults(EntityReference.class);
  }

  public <T extends EntityReference> List<T> getResults(Class<T> token)
      throws LuceneSearchException {
    return streamResults(token).collect(Collectors.toList());
  }

  public <T extends EntityReference> Stream<T> streamResults(Class<T> token)
      throws LuceneSearchException {
    try {
      return getSearchResultList().stream()
          .map(result -> References.asCompleteRef(result.getReference(), token));
    } catch (IllegalArgumentException iae) {
      throw new LuceneSearchException("Invalid token for query results", iae);
    }
  }

  public Map<EntityReference, Float> getResultsScoreMap(int offset, int limit)
      throws LuceneSearchException {
    return this.setOffset(offset).setLimit(limit).getResultsScoreMap();
  }

  public Map<EntityReference, Float> getResultsScoreMap() throws LuceneSearchException {
    Map<EntityReference, Float> ret = new LinkedHashMap<>();
    for (SearchResult result : getSearchResultList()) {
      ret.put(result.getReference(), result.getScore());
    }
    LOGGER.info("getResultsScoreMap: returning [{}] results for: {}", ret.size(), this);
    return ret;
  }

  private List<SearchResult> getSearchResultList() throws LuceneSearchException {
    SearchResults results = luceneSearch();
    int offset = (getOffset() <= 0 ? 1 : getOffset() + 1);
    int limit = (getLimit() < 0 ? getSize() : getLimit());
    return results.getResults(offset, limit);
  }

  public int getSize() throws LuceneSearchException {
    int hitcount;
    if (skipChecks) {
      hitcount = luceneSearch().getTotalHitcount();
    } else {
      hitcount = luceneSearch().getHitcount();
    }
    LOGGER.debug("getSize: returning [{}] for: {}", hitcount, this);
    return hitcount;
  }

  SearchResults luceneSearch() throws LuceneSearchException {
    try {
      if (searchResultsCache == null) {
        if (skipChecks) {
          searchResultsCache = getLucenePlugin().getSearchResultsWithoutChecks(queryString,
              getSortFieldsArray(), null, getLanguageString(), getContext());
        } else {
          searchResultsCache = getLucenePlugin().getSearchResults(queryString, getSortFieldsArray(),
              null, getLanguageString(), getContext());
        }
        LOGGER.trace("luceneSearch: new searchResults for: {}", this);
      } else {
        LOGGER.trace("luceneSearch: returning cached searchResults");
      }
      return searchResultsCache;
    } catch (IOException ioe) {
      throw newLuceneSearchException(ioe);
    } catch (ParseException exc) {
      throw newLuceneSearchException(exc);
    }
  }

  private LuceneSearchException newLuceneSearchException(Throwable cause) {
    return new LuceneSearchException("Error while executing lucene search query:" + queryString,
        cause);
  }

  @Override
  public String toString() {
    return "LuceneSearchResult [queryString=" + queryString + ", sortFields=" + sortFields
        + ", languages=" + languages + ", skipChecks=" + skipChecks + ", offset=" + offset
        + ", limit=" + limit + "]";
  }

  private LucenePlugin getLucenePlugin() {
    if (lucenePlugin == null) {
      lucenePlugin = (LucenePlugin) getContext().getWiki().getPlugin("lucene", getContext());
    }
    return lucenePlugin;
  }

  private XWikiContext getContext() {
    return Utils.getComponent(ModelContext.class).getXWikiContext();
  }

}
