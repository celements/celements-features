package com.celements.auth.user;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.reference.RefBuilder;
import com.celements.web.classes.oldcore.XWikiUsersClass;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.Utils;

public class CelementsUserTest extends AbstractComponentTest {

  private IModelAccessFacade modelAccessMock;
  private User testUser;

  @Before
  public void prepare() throws Exception {
    modelAccessMock = registerComponentMock(IModelAccessFacade.class);
    testUser = createNewUser();
  }

  @Test
  public void test_create_PER_LOOKUP() {
    replayDefault();
    assertNotNull(testUser);
    assertNotSame(testUser, createNewUser());
    verifyDefault();
  }

  @Test
  public void test_asXWikiUser_localUser() throws Exception {
    assertNotNull(testUser);
    DocumentReference userDocRef = RefBuilder.create()
        .wiki(getXContext().getDatabase())
        .space(XWikiConstant.XWIKI_SPACE)
        .doc("testUserDocName")
        .build(DocumentReference.class);
    createUserDoc(userDocRef);
    replayDefault();
    try {
      testUser.initialize(userDocRef);
      XWikiUser xwikiTestUser = testUser.asXWikiUser();
      assertNotNull(xwikiTestUser);
      assertEquals(XWikiConstant.XWIKI_SPACE + ".testUserDocName", xwikiTestUser.getUser());
    } catch (Exception exp) {
      fail("no exception expected: " + exp.getMessage() + " class: " + exp.getClass());
    }
    assertNotSame(testUser, createNewUser());
    verifyDefault();
  }

  @Test
  public void test_asXWikiUser_centralUser() throws Exception {
    assertNotNull(testUser);
    assertNotEquals("xwiki", getXContext().getDatabase());
    DocumentReference centralUserDocRef = RefBuilder.create()
        .wiki("xwiki")
        .space(XWikiConstant.XWIKI_SPACE)
        .doc("testUserDocName")
        .build(DocumentReference.class);
    createUserDoc(centralUserDocRef);
    replayDefault();
    try {
      testUser.initialize(centralUserDocRef);
      XWikiUser xwikiTestUser = testUser.asXWikiUser();
      assertNotNull(xwikiTestUser);
      assertEquals("xwiki:" + XWikiConstant.XWIKI_SPACE + ".testUserDocName",
          xwikiTestUser.getUser());
    } catch (Exception exp) {
      fail("no exception expected");
    }
    assertNotSame(testUser, createNewUser());
    verifyDefault();
  }

  private CelementsUser createNewUser() {
    return (CelementsUser) Utils.getComponent(User.class, CelementsUser.NAME);
  }

  private XWikiDocument createUserDoc(DocumentReference userDocRef)
      throws DocumentNotExistsException {
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    BaseObject userObj = new BaseObject();
    userObj.setXClassReference(XWikiUsersClass.CLASS_REF);
    userDoc.addXObject(userObj);
    expect(modelAccessMock.getDocument(userDocRef)).andReturn(userDoc).anyTimes();
    return userDoc;
  }

}
