package com.celements.mandatory;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.reference.RefBuilder;
import com.celements.web.classes.oldcore.XWikiGroupsClass;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.classes.BaseClass;

public class XWikiAdminUserTest extends AbstractComponentTest {

  private static final String CFG_KEY_ENABLED = "celements.mandatory.XWikiAdminUser.enabled";

  private XWikiAdminUser mandatory;

  @Before
  public void prepareTest() throws Exception {
    mandatory = getBeanFactory().getBean("celements.mandatory.XWikiAdminUser",
        XWikiAdminUser.class);
  }

  @Test
  public void testDependsOnMandatoryDocuments() throws Exception {
    assertEquals(2, mandatory.dependsOnMandatoryDocuments().size());
    assertEquals("celements.MandatoryGroups", mandatory.dependsOnMandatoryDocuments().get(0));
    assertEquals(MandatoryDiskDocuments.class.getName(), mandatory.dependsOnMandatoryDocuments().get(
        1));
  }

  @Test
  public void testIsEnabled_default() throws Exception {
    getXContext().setDatabase("xwiki");

    replayDefault();
    assertFalse(mandatory.isEnabledByDefault());
    assertFalse(mandatory.isEnabled());
    verifyDefault();
  }

  @Test
  public void testIsEnabled_configured() throws Exception {
    getXContext().setDatabase("xwiki");
    getConfigurationSource().setProperty(CFG_KEY_ENABLED, "1");

    replayDefault();
    assertTrue(mandatory.isEnabled());
    verifyDefault();
  }

  @Test
  public void testSkip_mainWiki() throws Exception {
    getXContext().setDatabase("xwiki");

    replayDefault();
    assertFalse(mandatory.skip());
    verifyDefault();
  }

  @Test
  public void testSkip_localWiki() throws Exception {
    getXContext().setDatabase("subwiki");

    replayDefault();
    assertTrue(mandatory.skip());
    verifyDefault();
  }

  @Test
  public void testAddAdminUserToAdminGroup_create() throws Exception {
    getXContext().setDatabase("xwiki");
    DocumentReference groupRef = getAdminGroupRef();
    XWikiDocument groupDoc = new XWikiDocument(groupRef);
    BaseClass baseClass = expectNewBaseObject(XWikiGroupsClass.CLASS_REF.getDocRef(
        groupRef.getWikiReference()));
    expect(baseClass.get(XWikiGroupsClass.FIELD_MEMBER.getName()))
        .andReturn(XWikiGroupsClass.FIELD_MEMBER.getXField())
        .anyTimes();

    replayDefault();
    assertTrue(mandatory.addAdminUserToAdminGroup(groupDoc));
    verifyDefault();

    assertEquals(1, XWikiObjectFetcher.on(groupDoc).filter(XWikiGroupsClass.CLASS_REF).count());
    assertEquals("XWiki." + XWikiAdminUser.ADMIN_DOC_NAME,
        XWikiObjectFetcher.on(groupDoc)
            .filter(XWikiGroupsClass.CLASS_REF)
            .stream()
            .findFirst()
            .orElseThrow()
            .getStringValue(XWikiGroupsClass.FIELD_MEMBER.getName()));
  }

  @Test
  public void testAddAdminUserToAdminGroup_exists() throws Exception {
    getXContext().setDatabase("xwiki");
    DocumentReference groupRef = getAdminGroupRef();
    XWikiDocument groupDoc = new XWikiDocument(groupRef);
    BaseClass baseClass = expectNewBaseObject(XWikiGroupsClass.CLASS_REF.getDocRef(
        groupRef.getWikiReference()));
    expect(baseClass.get(XWikiGroupsClass.FIELD_MEMBER.getName()))
        .andReturn(XWikiGroupsClass.FIELD_MEMBER.getXField())
        .anyTimes();

    replayDefault();
    assertTrue(mandatory.addAdminUserToAdminGroup(groupDoc));
    assertFalse(mandatory.addAdminUserToAdminGroup(groupDoc));
    verifyDefault();

    assertEquals(1, XWikiObjectFetcher.on(groupDoc).filter(XWikiGroupsClass.CLASS_REF).count());
  }

  private DocumentReference getAdminGroupRef() {
    return new RefBuilder().wiki("xwiki")
        .space(XWikiConstant.XWIKI_SPACE)
        .doc("XWikiAdminGroup")
        .build(DocumentReference.class);
  }

}
