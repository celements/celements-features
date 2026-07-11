package com.celements.tag;

import static com.celements.common.test.CelementsTestUtils.*;
import static com.celements.tag.classdefs.CelTagClass.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.tag.providers.CelTagsProvider;
import com.celements.wiki.WikiService;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.StaticListClass;
import com.xpn.xwiki.objects.classes.StringClass;

import one.util.streamex.StreamEx;

public class CelTagServiceTest extends AbstractComponentTest {

  private CelTagService service;
  private CelTagsProvider providerMock;

  @Before
  public void prepare() throws Exception {
    providerMock = registerComponentMock(CelTagsProvider.class);
    service = getBeanFactory().getBean(CelTagService.class);
    var wikiServiceMock = registerComponentMock(WikiService.class);
    expect(wikiServiceMock.streamAllWikis()).andReturn(StreamEx.empty()).anyTimes();
  }

  @Test
  public void test_addTags() throws Exception {
    var tagType = "type";
    var tagNames = List.of("tag1", "tag2", "tag3");
    var tags = StreamEx.of(tagNames)
        .map(tagName -> CelTag.builder().type(tagType).name(tagName))
        .toList();
    expect(providerMock.get()).andReturn(tags).anyTimes();

    var doc = new XWikiDocument(new DocumentReference(
        getXContext().getDatabase(), "Space", "TestDoc"));
    var tagXObj = new BaseObject();
    tagXObj.setXClassReference(CLASS_REF.getDocRef());
    tagXObj.setStringValue(FIELD_TYPE.getName(), tagType);
    tagXObj.setStringValue(FIELD_TAGS.getName(), StreamEx.of(tags)
        .limit(2)
        .map(t -> t.build().getName())
        .joining("|"));
    doc.addXObject(tagXObj);

    var xClass = createBaseClassMock(CLASS_REF.getDocRef());
    expectPropertyClass(xClass, FIELD_TYPE.getName(), new StringClass());
    expectPropertyClass(xClass, FIELD_TAGS.getName(), new StaticListClass());

    replayDefault();
    assertEquals(2, service.getDocTags(doc).count());
    assertFalse(service.addTags(doc, CelTag.builder().type(tagType).name("tag2").build()));
    assertEquals(2, service.getDocTags(doc).count());
    assertTrue(service.addTags(doc, CelTag.builder().type(tagType).name("tag3").build()));
    assertEquals(3, service.getDocTags(doc).count());
    assertFalse(service.addTags(doc, CelTag.builder().type(tagType).name("tag2").build()));
    assertEquals(3, service.getDocTags(doc).count());
    verifyDefault();
  }

  @Test
  public void test_setTags() throws Exception {
    var tagType = "type";
    expect(providerMock.get()).andReturn(List.of(
        CelTag.builder().type(tagType).name("tag3"))).anyTimes();
    var doc = new XWikiDocument(new DocumentReference(
        getXContext().getDatabase(), "Space", "TestDoc"));
    var tagXObj = new BaseObject();
    tagXObj.setXClassReference(CLASS_REF.getDocRef());
    tagXObj.setStringValue(FIELD_TYPE.getName(), tagType);
    tagXObj.setStringValue(FIELD_TAGS.getName(), "tag1|tag2");
    doc.addXObject(tagXObj);

    var xClass = createBaseClassMock(CLASS_REF.getDocRef());
    expectPropertyClass(xClass, FIELD_TYPE.getName(), new StringClass());
    expectPropertyClass(xClass, FIELD_TAGS.getName(), new StaticListClass());

    replayDefault();
    assertTrue(service.setTags(doc, tagType, " Tag3 ", "tag3"));
    assertEquals("tag3", tagXObj.getStringValue(FIELD_TAGS.getName()));
    assertFalse(service.setTags(doc, tagType, "tag3"));
    assertTrue(service.setTags(doc, tagType, (String[]) null));
    assertEquals("", tagXObj.getStringValue(FIELD_TAGS.getName()));
    assertFalse(service.setTags(doc, tagType, (String[]) null));
    verifyDefault();
  }

  @Test
  public void test_scope() throws Exception {
    var wiki = new WikiReference(getXContext().getDatabase());
    var otherWiki = new WikiReference("otherwiki");
    var doc = new XWikiDocument(new DocumentReference(wiki.getName(), "Space", "TestDoc"));
    var tagType = "type";
    var globalTag = CelTag.builder().type(tagType).name("global");
    var localTag = CelTag.builder().type(tagType).name("local").scope(wiki);
    var foreignTag = CelTag.builder().type(tagType).name("foreign").scope(otherWiki);
    expect(providerMock.get()).andReturn(List.of(globalTag, localTag, foreignTag)).anyTimes();

    var tagXObj = new BaseObject();
    tagXObj.setXClassReference(CLASS_REF.getDocRef());
    tagXObj.setStringValue(FIELD_TYPE.getName(), tagType);
    tagXObj.setStringValue(FIELD_TAGS.getName(), "global|local|foreign");
    doc.addXObject(tagXObj);

    var xClass = createBaseClassMock(CLASS_REF.getDocRef());
    expectPropertyClass(xClass, FIELD_TYPE.getName(), new StringClass());
    expectPropertyClass(xClass, FIELD_TAGS.getName(), new StaticListClass());

    replayDefault();
    assertEquals(List.of("global", "local"), service.streamTags(tagType, wiki)
        .map(CelTag::getName).toList());
    assertEquals(List.of("global", "foreign"), service.streamTags(tagType, otherWiki)
        .map(CelTag::getName).toList());
    assertEquals(List.of("global", "local"), service.getDocTags(doc, tagType)
        .map(CelTag::getName).toList());
    assertTrue(service.setTags(doc, tagType, "global", "local", "foreign", "unknown"));
    assertEquals("global|local", tagXObj.getStringValue(FIELD_TAGS.getName()));
    assertTrue(service.setTags(doc, tagType, "foreign", "unknown"));
    assertEquals("", tagXObj.getStringValue(FIELD_TAGS.getName()));
    CelTag global = service.getTag(tagType, "global").orElseThrow();
    CelTag local = service.getTag(tagType, "local").orElseThrow();
    CelTag foreign = service.getTag(tagType, "foreign").orElseThrow();
    assertTrue(service.addTags(doc, global, local, foreign));
    assertEquals("global|local", tagXObj.getStringValue(FIELD_TAGS.getName()));
    verifyDefault();
  }

}
