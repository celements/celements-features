package com.celements.mandatory;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.classes.ClassDefinition;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.reference.RefBuilder;
import com.celements.web.classes.oldcore.XWikiGroupsClass;
import com.celements.web.classes.oldcore.XWikiUsersClass;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.PasswordClass;

public class MainAdminUserTest extends AbstractComponentTest {

  private static final String CFG_KEY_ENABLED = "celements.mandatory.enabled.MainAdminUser";
  private static final String ADMIN_PASSWORD = "admin";

  private MainAdminUser mandatory;

  @Before
  public void prepareTest() throws Exception {
    registerComponentMock(IModelAccessFacade.class);
    mandatory = getBeanFactory().getBean("celements.mandatory.MainAdminUser",
        MainAdminUser.class);
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
    setAdminPassword(ADMIN_PASSWORD);

    replayDefault();
    assertTrue(mandatory.isEnabled());
    verifyDefault();
  }

  @Test
  public void testIsEnabled_configured_blank() throws Exception {
    getXContext().setDatabase("xwiki");
    setAdminPassword(" ");

    replayDefault();
    assertFalse(mandatory.isEnabled());
    verifyDefault();
  }

  @Test
  public void testIsEnabled_enabledFlagWithoutPassword_unconditionalOverride() throws Exception {
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
  public void testCheckDocumentsMain_activatesAdminAndAddsToGroup() throws Exception {
    getXContext().setDatabase("xwiki");
    setAdminPassword(ADMIN_PASSWORD);
    DocumentReference groupRef = getAdminGroupRef();
    XWikiDocument groupDoc = new XWikiDocument(groupRef);
    DocumentReference adminUserRef = getAdminUserRef();
    XWikiDocument adminUserDoc = newAdminUserDoc(false);
    expect(getMock(IModelAccessFacade.class).getDocument(adminUserRef)).andReturn(adminUserDoc);
    getMock(IModelAccessFacade.class).saveDocument(adminUserDoc, "activate account");
    expectXWikiUsersClass();
    expectNewGroupsBaseObject(groupRef);

    replayDefault();
    assertTrue(mandatory.checkDocumentsMain(groupDoc));
    verifyDefault();

    assertEquals(1, XWikiObjectFetcher.on(groupDoc).filter(XWikiGroupsClass.CLASS_REF).count());
    assertEquals(1, getAdminUserObj(adminUserDoc).getIntValue(
        XWikiUsersClass.FIELD_ACTIVE.getName()));
    assertEquals(new PasswordClass().getEquivalentPassword("hash:SHA-512:", ADMIN_PASSWORD),
        getAdminUserObj(adminUserDoc).getStringValue(XWikiUsersClass.FIELD_PASSWORD.getName()));
  }

  @Test
  public void testEnableAdminUser_missingUser() throws Exception {
    getXContext().setDatabase("xwiki");
    DocumentReference adminUserRef = getAdminUserRef();
    expect(getMock(IModelAccessFacade.class).getDocument(adminUserRef))
        .andThrow(new DocumentNotExistsException(adminUserRef));

    replayDefault();
    assertFalse(mandatory.enableAdminUser(ADMIN_PASSWORD));
    verifyDefault();
  }

  @Test
  public void testAddAdminUserToAdminGroup_create() throws Exception {
    getXContext().setDatabase("xwiki");
    DocumentReference groupRef = getAdminGroupRef();
    XWikiDocument groupDoc = new XWikiDocument(groupRef);
    expectNewGroupsBaseObject(groupRef);

    replayDefault();
    assertTrue(mandatory.addAdminUserToAdminGroup(groupDoc));
    verifyDefault();

    assertEquals(1, XWikiObjectFetcher.on(groupDoc).filter(XWikiGroupsClass.CLASS_REF).count());
    assertEquals("XWiki." + MainAdminUser.ADMIN_DOC_NAME,
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
    expectNewGroupsBaseObject(groupRef);

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

  private void setAdminPassword(String password) {
    getConfigurationSource().setProperty(MainAdminUser.CFG_KEY_ADMIN_PASSWORD, password);
  }

  private DocumentReference getAdminUserRef() {
    return new RefBuilder().wiki("xwiki")
        .space(XWikiConstant.XWIKI_SPACE)
        .doc(MainAdminUser.ADMIN_DOC_NAME)
        .build(DocumentReference.class);
  }

  private XWikiDocument newAdminUserDoc(boolean active) {
    XWikiDocument doc = new XWikiDocument(getAdminUserRef());
    BaseObject obj = new BaseObject();
    obj.setXClassReference(XWikiUsersClass.CLASS_REF.getDocRef(doc.getDocumentReference()
        .getWikiReference()));
    obj.setIntValue(XWikiUsersClass.FIELD_ACTIVE.getName(), active ? 1 : 0);
    doc.addXObject(obj);
    return doc;
  }

  private BaseObject getAdminUserObj(XWikiDocument adminUserDoc) {
    return XWikiObjectFetcher.on(adminUserDoc)
        .filter(XWikiUsersClass.CLASS_REF)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  private void expectXWikiUsersClass() throws Exception {
    ClassDefinition classDef = getBeanFactory().getBean(XWikiUsersClass.CLASS_DEF_HINT,
        ClassDefinition.class);
    BaseClass baseClass = expectNewBaseObject(classDef.getDocRef(getAdminUserRef()
        .getWikiReference()));
    for (ClassField<?> field : classDef.getFields()) {
      expect(baseClass.get(field.getName())).andReturn(field.getXField()).anyTimes();
    }
  }

  private void expectNewGroupsBaseObject(DocumentReference groupRef) throws Exception {
    BaseClass baseClass = expectNewBaseObject(XWikiGroupsClass.CLASS_REF.getDocRef(
        groupRef.getWikiReference()));
    expect(baseClass.get(XWikiGroupsClass.FIELD_MEMBER.getName()))
        .andReturn(XWikiGroupsClass.FIELD_MEMBER.getXField())
        .anyTimes();
  }

}
