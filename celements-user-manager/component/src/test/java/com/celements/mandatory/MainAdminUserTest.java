package com.celements.mandatory;

import static com.celements.common.test.CelementsTestUtils.*;
import static com.xpn.xwiki.XWikiConstant.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;

import com.celements.auth.MainAdminConfig;
import com.celements.auth.user.User;
import com.celements.auth.user.UserInstantiationException;
import com.celements.auth.user.UserService;
import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.classes.ClassDefinition;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.reference.RefBuilder;
import com.celements.web.classes.oldcore.XWikiUsersClass;
import com.xpn.xwiki.XWikiException;
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
    registerComponentMock(UserService.class);
    registerComponentMock(MainAdminConfig.class);
    expect(getMock(MainAdminConfig.class).isAutoLoginEnabled()).andStubReturn(false);
    expect(getMock(MainAdminConfig.class).getPassword()).andStubReturn(Optional.empty());
    expect(getMock(MainAdminConfig.class).getUserDocRef()).andStubReturn(getAdminUserRef());
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
    expect(getMock(MainAdminConfig.class).getPassword())
        .andReturn(Optional.of(ADMIN_PASSWORD));

    replayDefault();
    assertTrue(mandatory.isEnabled());
    verifyDefault();
  }

  @Test
  public void testIsEnabled_autoLogin() throws Exception {
    getXContext().setDatabase("xwiki");
    expect(getMock(MainAdminConfig.class).isAutoLoginEnabled()).andReturn(true);

    replayDefault();
    assertTrue(mandatory.isEnabled());
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
    expect(getMock(MainAdminConfig.class).getPassword())
        .andReturn(Optional.of(ADMIN_PASSWORD)).times(2);
    DocumentReference adminUserRef = getAdminUserRef();
    XWikiDocument adminUserDoc = newAdminUserDoc(false);
    User adminUser = createDefaultMock(User.class);
    expect(getMock(UserService.class).getUser(adminUserRef)).andReturn(adminUser);
    expect(adminUser.getDocument()).andReturn(adminUserDoc);
    getMock(IModelAccessFacade.class).saveDocument(adminUserDoc, "activate account");
    expectXWikiUsersClass();
    expect(getMock(UserService.class).addUserToGroup(adminUser, getAdminGroupRef()))
        .andReturn(true);

    replayDefault();
    assertFalse(mandatory.checkDocumentsMain(new XWikiDocument(adminUserRef)));
    verifyDefault();

    assertEquals(1, getAdminUserObj(adminUserDoc).getIntValue(
        XWikiUsersClass.FIELD_ACTIVE.getName()));
    assertEquals(new PasswordClass().getEquivalentPassword("hash:SHA-512:", ADMIN_PASSWORD),
        getAdminUserObj(adminUserDoc).getStringValue(XWikiUsersClass.FIELD_PASSWORD.getName()));
  }

  @Test
  public void testCheckDocumentsMain_missingUser() throws Exception {
    getXContext().setDatabase("xwiki");
    DocumentReference adminUserRef = getAdminUserRef();
    expect(getMock(UserService.class).getUser(adminUserRef))
        .andThrow(new UserInstantiationException("missing"));

    replayDefault();
    assertThrows(XWikiException.class,
        () -> mandatory.checkDocumentsMain(new XWikiDocument(adminUserRef)));
    verifyDefault();
  }

  @Test
  public void testCheckDocumentsMain_withoutPassword_addsToGroupOnly() throws Exception {
    getXContext().setDatabase("xwiki");
    DocumentReference adminUserRef = getAdminUserRef();
    User adminUser = createDefaultMock(User.class);
    expect(getMock(UserService.class).getUser(adminUserRef)).andReturn(adminUser);
    expect(getMock(UserService.class).addUserToGroup(adminUser, getAdminGroupRef()))
        .andReturn(false);

    replayDefault();
    assertFalse(mandatory.checkDocumentsMain(new XWikiDocument(adminUserRef)));
    verifyDefault();
  }

  @Test
  public void testCheckDocumentsMain_autoLogin_activatesWithRandomPassword() throws Exception {
    getXContext().setDatabase("xwiki");
    expect(getMock(MainAdminConfig.class).isAutoLoginEnabled()).andReturn(true);
    DocumentReference adminUserRef = getAdminUserRef();
    XWikiDocument adminUserDoc = newAdminUserDoc(false);
    adminUserDoc.getXObject(XWikiUsersClass.CLASS_REF.getDocRef(
        adminUserRef.getWikiReference())).setStringValue(
            XWikiUsersClass.FIELD_PASSWORD.getName(), "stored-hash");
    User adminUser = createDefaultMock(User.class);
    expect(getMock(UserService.class).getUser(adminUserRef)).andReturn(adminUser);
    expect(adminUser.getDocument()).andReturn(adminUserDoc);
    getMock(IModelAccessFacade.class).saveDocument(adminUserDoc, "activate account");
    expectXWikiUsersClass();
    expect(getMock(UserService.class).addUserToGroup(adminUser, getAdminGroupRef()))
        .andReturn(true);

    replayDefault();
    assertFalse(mandatory.checkDocumentsMain(new XWikiDocument(adminUserRef)));
    verifyDefault();

    assertEquals(1, getAdminUserObj(adminUserDoc).getIntValue(
        XWikiUsersClass.FIELD_ACTIVE.getName()));
    String passwordHash = getAdminUserObj(adminUserDoc).getStringValue(
        XWikiUsersClass.FIELD_PASSWORD.getName());
    assertFalse(passwordHash.isEmpty());
    assertNotEquals("stored-hash", passwordHash);
  }

  private ClassReference getAdminGroupRef() {
    return new ClassReference(XWIKI_SPACE, "XWikiAdminGroup");
  }

  private DocumentReference getAdminUserRef() {
    return new RefBuilder().wiki("xwiki")
        .space(XWIKI_SPACE)
        .doc("Admin")
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

}
