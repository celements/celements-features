package com.celements.search.lucene;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.common.date.DateFormat;
import com.celements.common.test.AbstractComponentTest;
import com.celements.search.lucene.index.analysis.CelementsSimpleAnalyzer;
import com.celements.search.lucene.query.IQueryRestriction;
import com.celements.search.lucene.query.LuceneQuery;
import com.celements.search.lucene.query.QueryRestriction;
import com.celements.search.lucene.query.QueryRestriction.QueryMode;
import com.celements.search.lucene.query.QueryRestrictionGroup;
import com.celements.search.lucene.query.QueryRestrictionGroup.Type;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.plugin.lucene.IndexFields;
import com.xpn.xwiki.plugin.lucene.LucenePlugin;
import com.xpn.xwiki.web.Utils;

public class LuceneSearchServiceTest extends AbstractComponentTest {

  private ILuceneSearchService searchService;
  private XWiki xwiki;
  private XWikiContext context;
  private LucenePlugin plugin;

  @Before
  public void prepare() throws Exception {
    xwiki = getWikiMock();
    context = getContext();
    plugin = createDefaultMock(LucenePlugin.class);
    expect(xwiki.getPlugin(eq("lucene"), same(getContext()))).andReturn(plugin).anyTimes();
    searchService = Utils.getComponent(ILuceneSearchService.class);
  }

  @Test
  public void test_createQuery() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    LuceneQuery query = searchService.createQuery();
    assertNotNull(query);
    assertEquals("(type:(+\"wikipage\") AND wiki:(+\"xwikidb\"))", query.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createQuery_nullType() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    LuceneQuery query = searchService.createQuery(null);
    assertNotNull(query);
    assertEquals("((type:(+\"wikipage\") OR type:(+\"attachment\")) " + "AND wiki:(+\"xwikidb\"))",
        query.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createQuery_noType() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    LuceneQuery query = searchService.createQuery(Collections.<String>emptyList());
    assertNotNull(query);
    assertEquals("((type:(+\"wikipage\") OR type:(+\"attachment\")) " + "AND wiki:(+\"xwikidb\"))",
        query.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createQuery_multiType() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    LuceneQuery query = searchService.createQuery(Arrays.asList("wikipage", "attachment"));
    assertNotNull(query);
    assertEquals("((type:(+\"wikipage\") OR type:(+\"attachment\")) " + "AND wiki:(+\"xwikidb\"))",
        query.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAndRestrictionGroup() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestrictionGroup restrGrp = searchService.createRestrictionGroup(Type.AND);
    assertNotNull(restrGrp);
    assertEquals(Type.AND, restrGrp.getType());
    assertEquals(0, restrGrp.size());
    verifyDefault();
  }

  @Test
  public void test_createOrRestrictionGroup() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestrictionGroup restrGrp = searchService.createRestrictionGroup(Type.OR);
    assertNotNull(restrGrp);
    assertEquals(Type.OR, restrGrp.getType());
    assertEquals(0, restrGrp.size());
    verifyDefault();
  }

  @Test
  public void test_createRestrictionGroup() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> fields = Arrays.asList("field1", "field2", "field3");
    List<String> values = Arrays.asList("value1", "value2", "value3");
    QueryRestrictionGroup restrGrp = searchService.createRestrictionGroup(Type.AND, fields, values);
    assertNotNull(restrGrp);
    assertEquals(Type.AND, restrGrp.getType());
    assertEquals(3, restrGrp.size());
    assertEquals("(field1:(+value1*) AND field2:(+value2*) AND field3:(+value3*))",
        restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestrictionGroup_oneField() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> fields = Arrays.asList("field");
    List<String> values = Arrays.asList("value1", "value2", "value3");
    QueryRestrictionGroup restrGrp = searchService.createRestrictionGroup(Type.OR, fields, values);
    assertNotNull(restrGrp);
    assertEquals(Type.OR, restrGrp.getType());
    assertEquals(3, restrGrp.size());
    assertEquals("(field:(+value1*) OR field:(+value2*) OR field:(+value3*))",
        restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestrictionGroup_twoValues() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> fields = Arrays.asList("field1", "field2", "field3");
    List<String> values = Arrays.asList("value1", "value2");
    QueryRestrictionGroup restrGrp = searchService.createRestrictionGroup(Type.AND, fields, values);
    assertNotNull(restrGrp);
    assertEquals(Type.AND, restrGrp.getType());
    assertEquals(3, restrGrp.size());
    assertEquals("(field1:(+value1*) AND field2:(+value2*) AND field3:(+value2*))",
        restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestriction() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createRestriction("XWiki.XWikiUsers." + "first_name",
        "Hans");
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.first_name:(+Hans*)", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestriction_false_true() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createRestriction("XWiki.XWikiUsers." + "first_name",
        "Hans", false, true);
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.first_name:(Hans~)", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestriction_nullField() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    assertEquals("", searchService.createRestriction((String) null, "Hans").getQueryString());
    assertEquals("", searchService.createRestriction("", "Hans").getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestriction_nullVal() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    assertEquals("", searchService.createRestriction("Hans", null).getQueryString());
    assertEquals("", searchService.createRestriction("Hans", "").getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createRestriction_withAnalyzer() {
    try (CelementsSimpleAnalyzer analyzer = new CelementsSimpleAnalyzer(LucenePlugin.VERSION)) {
      expect(plugin.getAnalyzer()).andReturn(analyzer).anyTimes();
      replayDefault();
      assertEquals("Field:(hansoe)",
          searchService.createRestriction("Field", "Hänsôè", false).getQueryString());
      assertEquals("Field:(+hansoe*)",
          searchService.createRestriction("Field", "Hänsôè", true).getQueryString());
      assertEquals("Field:(+forum* +wort* +musik*)",
          searchService.createRestriction("Field", "Forum für Wort und Musik", true)
              .getQueryString());
      assertEquals("", searchService.createRestriction("Field", "für", true).getQueryString());
      assertEquals("", searchService.createRestriction("Field", "für")
          .setMode(QueryMode.EXACT).getQueryString());
      assertEquals("", searchService.createRestriction("Field", "und", true).getQueryString());
      verifyDefault();
    }
  }

  @Test
  public void test_createSpaceRestriction() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createSpaceRestriction(new SpaceReference("spaceName",
        new WikiReference("wiki")));
    assertNotNull(restr);
    assertEquals("space:(+\"spaceName\")", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createFieldRestriction() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    DocumentReference classRef = new DocumentReference("db", "ClassSpace", "MyClass");
    QueryRestriction restr = searchService.createFieldRestriction(classRef, "someField",
        "val1 val2");
    assertNotNull(restr);
    assertEquals("ClassSpace.MyClass.someField:(+val1* +val2*)", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createFieldRestriction_notTokenized() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    DocumentReference classRef = new DocumentReference("db", "ClassSpace", "MyClass");
    QueryRestriction restr = searchService.createFieldRestriction(classRef, "someField",
        "val1 val2", false);
    assertNotNull(restr);
    assertEquals("ClassSpace.MyClass.someField:(val1 val2)", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createFieldRestriction_nullClassRef() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createFieldRestriction(null, "someField", "val");
    assertNull(restr);
    verifyDefault();
  }

  @Test
  public void test_createFieldRestriction_emptyField() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    DocumentReference classRef = new DocumentReference("db", "ClassSpace", "MyClass");
    QueryRestriction restr = searchService.createFieldRestriction(classRef, "", "val");
    assertNull(restr);
    verifyDefault();
  }

  @Test
  public void test_createFieldRefRestriction() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    DocumentReference classRef = new DocumentReference("db", "ClassSpace", "MyClass");
    EntityReference ref = new DocumentReference("db", "ToSpace", "ToPage");
    IQueryRestriction restr = searchService.createFieldRefRestriction(classRef, "ref", ref);
    assertNotNull(restr);
    assertEquals("(ClassSpace.MyClass.ref:(+\"db\\:ToSpace.ToPage\") OR "
        + "ClassSpace.MyClass.ref:(+\"ToSpace.ToPage\"))", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createFieldRefRestriction_otherdb() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    DocumentReference classRef = new DocumentReference("db", "ClassSpace", "MyClass");
    EntityReference ref = new DocumentReference("otherdb", "ToSpace", "ToPage");
    IQueryRestriction restr = searchService.createFieldRefRestriction(classRef, "ref", ref);
    assertNotNull(restr);
    assertEquals("ClassSpace.MyClass.ref:(+\"otherdb\\:ToSpace.ToPage\")", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void testRangeRestriction() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createRangeRestriction("XWiki."
        + "XWikiUsers.first_name", "Hans", "Peter");
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.first_name:([Hans TO Peter])", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void testRangeRestrictionExclusive() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createRangeRestriction("XWiki."
        + "XWikiUsers.first_name", "Hans", "Peter", false);
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.first_name:({Hans TO Peter})", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void testRangeRestrictionInclusive() {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestriction restr = searchService.createRangeRestriction("XWiki."
        + "XWikiUsers.first_name", "Hans", "Peter", true);
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.first_name:([Hans TO Peter])", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createDateRestriction() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    Instant date = IndexFields.DATE_PARSER.apply("199001151213").toInstant();
    QueryRestriction restr = searchService.createDateRestriction("XWiki." + "XWikiUsers.date",
        Date.from(date));
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.date:(199001151213)", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createFromToDateRestriction() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    Instant fromDate = IndexFields.DATE_PARSER.apply("111111111111").toInstant();
    Instant toDate = IndexFields.DATE_PARSER.apply("199001151213").toInstant();
    QueryRestriction restr = searchService.createFromToDateRestriction("XWiki." + "XWikiUsers.date",
        Date.from(fromDate), Date.from(toDate), true);
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.date:([111111111111 TO 199001151213])", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createToDateRestriction() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    Instant date = IndexFields.DATE_PARSER.apply("199001151213").toInstant();
    QueryRestriction restr = searchService.createToDateRestriction("XWiki." + "XWikiUsers.date",
        Date.from(date), true);
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.date:([0 TO 199001151213])", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createFromDateRestriction() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    Instant date = DateFormat.parser(IndexFields.DATE_FORMAT).apply("199001151213").toInstant();
    QueryRestriction restr = searchService.createFromDateRestriction("XWiki." + "XWikiUsers.date",
        Date.from(date), true);
    assertNotNull(restr);
    assertEquals("XWiki.XWikiUsers.date:([199001151213 TO zzzzzzzzzzzz])", restr.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAttachmentRestrictionGroup_mimetypes() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> mimetypes = Arrays.asList("application/pdf");
    QueryRestrictionGroup restrGrp = searchService.createAttachmentRestrictionGroup(mimetypes, null,
        null);
    assertNotNull(restrGrp);
    assertEquals("mimetype:(+\"application/pdf\")", restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAttachmentRestrictionGroup_mimetypesBlackList() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> mimetypesBlackList = Arrays.asList("text");
    QueryRestrictionGroup restrGrp = searchService.createAttachmentRestrictionGroup(null,
        mimetypesBlackList, null);
    assertNotNull(restrGrp);
    assertEquals("NOT mimetype:(+\"text\")", restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAttachmentRestrictionGroup_filenames() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> filenamePrefs = Arrays.asList("Asdf");
    QueryRestrictionGroup restrGrp = searchService.createAttachmentRestrictionGroup(null, null,
        filenamePrefs);
    assertNotNull(restrGrp);
    assertEquals("filename:(+Asdf*)", restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAttachmentRestrictionGroup_multi() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    List<String> mimetypes = Arrays.asList("image", "application/pdf");
    List<String> mimetypesBlackList = Arrays.asList("text", "video");
    List<String> filenamePrefs = Arrays.asList("Asdf", "Fdsa");
    QueryRestrictionGroup restrGrp = searchService.createAttachmentRestrictionGroup(mimetypes,
        mimetypesBlackList, filenamePrefs);
    assertNotNull(restrGrp);
    assertEquals("((mimetype:(+\"image\") OR mimetype:(+\"application/pdf\")) "
        + "AND NOT (mimetype:(+\"text\") OR mimetype:(+\"video\")) "
        + "AND (filename:(+Asdf*) OR filename:(+Fdsa*)))", restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAttachmentRestrictionGroup_empty() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestrictionGroup restrGrp = searchService.createAttachmentRestrictionGroup(
        new ArrayList<String>(), new ArrayList<String>(), new ArrayList<String>());
    assertNotNull(restrGrp);
    assertEquals("", restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void test_createAttachmentRestrictionGroup_null() throws Exception {
    expect(plugin.getAnalyzer()).andReturn(null).anyTimes();
    replayDefault();
    QueryRestrictionGroup restrGrp = searchService.createAttachmentRestrictionGroup(null, null,
        null);
    assertNotNull(restrGrp);
    assertEquals("", restrGrp.getQueryString());
    verifyDefault();
  }

  @Test
  public void testGetResultLimit() {
    int limit = 1234;
    expect(plugin.getResultLimit(eq(false), same(context))).andReturn(limit).once();

    replayDefault();
    int ret = searchService.getResultLimit();
    verifyDefault();

    assertEquals(limit, ret);
  }

  @Test
  public void testGetResultLimit_skipChecks() {
    int limit = 1234;
    expect(plugin.getResultLimit(eq(true), same(context))).andReturn(limit).once();

    replayDefault();
    int ret = searchService.getResultLimit(true);
    verifyDefault();

    assertEquals(limit, ret);
  }

  @Test
  public void test_date_pattern() {
    assertFalse(LuceneSearchService.DATE_PATTERN.matcher("asdf").matches());
    assertFalse(LuceneSearchService.DATE_PATTERN.matcher("20170101").matches());
    assertTrue(LuceneSearchService.DATE_PATTERN.matcher("20170101*").matches());
    assertTrue(LuceneSearchService.DATE_PATTERN.matcher("201701012015").matches());
  }

}
