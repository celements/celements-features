package com.celements.search.lucene;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.lucene.queryParser.ParseException;
import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.search.lucene.query.LuceneDocType;
import com.celements.search.lucene.query.LuceneQuery;
import com.google.common.collect.ImmutableList;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.plugin.lucene.LucenePlugin;
import com.xpn.xwiki.plugin.lucene.SearchResult;
import com.xpn.xwiki.plugin.lucene.SearchResults;

public class LuceneSearchResultTest extends AbstractComponentTest {

  private XWikiContext context;
  private LucenePlugin lucenePluginMock;

  @Before
  public void prepare() throws Exception {
    context = getContext();
    lucenePluginMock = createDefaultMock(LucenePlugin.class);
  }

  @Test
  public void test_Getters() {
    LuceneQuery query = new LuceneQuery();
    List<String> sortFields = Arrays.asList("sort1", "sort2");
    List<String> languages = Arrays.asList("lang1", "lang2");
    boolean skipChecks = false;
    LuceneSearchResult result = newResult(query, sortFields, languages, skipChecks);
    assertEquals(query.getQueryString(), result.getQueryString());
    assertEquals(sortFields, result.getSortFields());
    assertEquals(languages, result.getLanguages());
    assertEquals(skipChecks, result.isSkipChecks());
  }

  @Test
  public void test_getSetOffset() {
    LuceneSearchResult result = newResult(new LuceneQuery(), null, null, false);
    assertEquals(0, result.getOffset());
    result.searchResultsCache = createDefaultMock(SearchResults.class);
    result.setOffset(6);
    assertEquals(6, result.getOffset());
    assertNull("setOffset should reset the cache", result.searchResultsCache);
  }

  @Test
  public void test_getSetLimit() {
    LuceneSearchResult result = newResult(new LuceneQuery(), null, null, false);
    assertEquals(-1, result.getLimit());
    result.searchResultsCache = createDefaultMock(SearchResults.class);
    result.setLimit(6);
    assertEquals(6, result.getLimit());
    assertNull("setLimit should reset the cache", result.searchResultsCache);
  }

  @Test
  public void test_getResults() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    result.setOffset(6);
    result.setLimit(10);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.searchResultsCache = sResultsMock;

    List<SearchResult> list = new ArrayList<>();
    list.add(createDefaultMock(SearchResult.class));
    list.add(createDefaultMock(SearchResult.class));
    expect(sResultsMock.getResults(eq(7), eq(10))).andReturn(list).once();
    DocumentReference docRef = new DocumentReference("db", "space", "doc");
    expect(list.get(0).getReference()).andReturn(docRef).once();
    AttachmentReference attRef = new AttachmentReference("file", docRef);
    expect(list.get(1).getReference()).andReturn(attRef).once();

    replayDefault();
    List<EntityReference> ret = result.getResults();
    verifyDefault();

    assertEquals(Arrays.asList(docRef, attRef), ret);
  }

  @Test
  public void test_getResults_empty() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.searchResultsCache = sResultsMock;
    int totalCount = 10;

    expect(sResultsMock.getTotalHitcount()).andReturn(totalCount).once();
    expect(sResultsMock.getResults(eq(1), eq(totalCount))).andReturn(
        Collections.<SearchResult>emptyList()).once();

    replayDefault();
    List<EntityReference> ret = result.getResults();
    verifyDefault();

    assertNotNull(ret);
    assertEquals(0, ret.size());
  }

  @Test
  public void test_getResults_negativeOffsetLimit() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = false;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.setOffset(-10);
    result.setLimit(-10);
    result.searchResultsCache = sResultsMock;

    expect(sResultsMock.getHitcount()).andReturn(1234);
    List<SearchResult> list = new ArrayList<>();
    list.add(createDefaultMock(SearchResult.class));
    expect(sResultsMock.getResults(eq(1), eq(1234))).andReturn(list).once();
    DocumentReference docRef = new DocumentReference("db", "space", "doc");
    expect(list.get(0).getReference()).andReturn(docRef).once();

    replayDefault();
    List<EntityReference> ret = result.getResults();
    verifyDefault();

    assertEquals(Arrays.asList(docRef), ret);
  }

  @Test
  public void test_getResultsScoreMap() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.setOffset(6);
    result.setLimit(10);
    result.searchResultsCache = sResultsMock;

    List<SearchResult> list = new ArrayList<>();
    list.add(createDefaultMock(SearchResult.class));
    list.add(createDefaultMock(SearchResult.class));
    expect(sResultsMock.getResults(eq(7), eq(10))).andReturn(list).once();
    DocumentReference docRef = new DocumentReference("db", "space", "doc");
    expect(list.get(0).getReference()).andReturn(docRef).once();
    Float docScore = 0.3f;
    expect(list.get(0).getScore()).andReturn(docScore).once();
    AttachmentReference attRef = new AttachmentReference("file", docRef);
    expect(list.get(1).getReference()).andReturn(attRef).once();
    Float attScore = 0.5f;
    expect(list.get(1).getScore()).andReturn(attScore).once();

    replayDefault();
    Map<EntityReference, Float> ret = result.getResultsScoreMap();
    verifyDefault();

    assertEquals(2, ret.size());
    assertTrue(ret.containsKey(docRef));
    assertEquals(docScore, ret.get(docRef));
    assertTrue(ret.containsKey(attRef));
    assertEquals(attScore, ret.get(attRef));
  }

  @Test
  public void test_getResultsScoreMap_empty() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.searchResultsCache = sResultsMock;
    int totalCount = 10;

    expect(sResultsMock.getTotalHitcount()).andReturn(totalCount).once();
    expect(sResultsMock.getResults(eq(1), eq(totalCount))).andReturn(
        Collections.<SearchResult>emptyList()).once();

    replayDefault();
    Map<EntityReference, Float> ret = result.getResultsScoreMap();
    verifyDefault();

    assertNotNull(ret);
    assertEquals(0, ret.size());
  }

  @Test
  public void test_getSize() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = false;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.searchResultsCache = sResultsMock;

    expect(sResultsMock.getHitcount()).andReturn(1234);

    replayDefault();
    int ret = result.getSize();
    verifyDefault();

    assertEquals(1234, ret);
  }

  @Test
  public void test_getSize_skipChecks() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.searchResultsCache = sResultsMock;

    expect(sResultsMock.getTotalHitcount()).andReturn(1234);

    replayDefault();
    int ret = result.getSize();
    verifyDefault();

    assertEquals(1234, ret);
  }

  @Test
  public void test_LuceneSearch_alreadySet() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);
    result.searchResultsCache = sResultsMock;

    replayDefault();
    SearchResults ret = result.luceneSearch();
    verifyDefault();

    assertSame(sResultsMock, ret);
  }

  @Test
  public void test_LuceneSearch_withChecks() throws Exception {
    LuceneQuery query = new LuceneQuery();
    List<String> sortFields = Arrays.asList("sort1", "sort2");
    List<String> languages = Arrays.asList("lang1", "lang2");
    boolean skipChecks = false;
    LuceneSearchResult result = newResult(query, sortFields, languages, skipChecks);
    Capture<String[]> sortFieldsCapture = newCapture();
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);

    expect(lucenePluginMock.getSearchResults(eq(query.getQueryString()), capture(sortFieldsCapture),
        isNull(String.class), eq("lang1,lang2"), same(context))).andReturn(sResultsMock).once();

    replayDefault();
    SearchResults ret = result.luceneSearch();
    verifyDefault();

    assertEquals(sortFields, Arrays.asList(sortFieldsCapture.getValue()));
    assertSame(sResultsMock, ret);
  }

  @Test
  public void test_LuceneSearch_withoutChecks() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = true;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);
    Capture<String[]> sortFieldsCapture = newCapture();
    SearchResults sResultsMock = createDefaultMock(SearchResults.class);

    expect(lucenePluginMock.getSearchResultsWithoutChecks(eq(query.getQueryString()), capture(
        sortFieldsCapture), isNull(String.class), eq(""), same(context))).andReturn(
            sResultsMock).once();

    replayDefault();
    SearchResults ret = result.luceneSearch();
    verifyDefault();

    assertEquals(Collections.emptyList(), Arrays.asList(sortFieldsCapture.getValue()));
    assertSame(sResultsMock, ret);
  }

  @Test
  public void test_LuceneSearch_IOException() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = false;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);

    expect(lucenePluginMock.getSearchResults(eq(query.getQueryString()), anyObject(String[].class),
        isNull(String.class), eq(""), same(context))).andThrow(new IOException()).once();

    replayDefault();
    try {
      result.luceneSearch();
      fail("expected LuceneSearchException");
    } catch (LuceneSearchException lse) {
      // expected
    }
    verifyDefault();
  }

  @Test
  public void test_LuceneSearch_ParseException() throws Exception {
    LuceneQuery query = new LuceneQuery();
    boolean skipChecks = false;
    LuceneSearchResult result = newResult(query, null, null, skipChecks);

    expect(lucenePluginMock.getSearchResults(eq(query.getQueryString()), anyObject(String[].class),
        isNull(String.class), eq(""), same(context))).andThrow(new ParseException()).once();

    replayDefault();
    try {
      result.luceneSearch();
      fail("expected LuceneSearchException");
    } catch (LuceneSearchException lse) {
      // expected
    }
    verifyDefault();
  }

  private LuceneSearchResult newResult(LuceneQuery query, List<String> sortFields,
      List<String> languages, boolean skipChecks) {
    query.setDocTypes(ImmutableList.of(LuceneDocType.DOC));
    query.setWiki(new WikiReference(context.getDatabase()));
    LuceneSearchResult result = new LuceneSearchResult(query, sortFields, languages, skipChecks);
    result.lucenePlugin = lucenePluginMock;
    return result;
  }

}
