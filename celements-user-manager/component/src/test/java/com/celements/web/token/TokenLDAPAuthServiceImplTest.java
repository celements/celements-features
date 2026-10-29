/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.celements.web.token;

import static com.celements.common.test.CelementsTestUtils.*;
import static com.celements.web.token.TokenLDAPAuthServiceImpl.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.easymock.Capture;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;

import com.celements.auth.MainAdminConfig;
import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.classes.ClassDefinition;
import com.celements.web.classes.oldcore.XWikiUsersClass;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;

public class TokenLDAPAuthServiceImplTest extends AbstractComponentTest {

  private static final String ADMIN_USER_FULL_NAME = "xwiki:XWiki.Admin";

  private TokenLDAPAuthServiceImpl tokenAuthImpl;
  private XWikiStoreInterface store;
  private MainAdminConfig mainAdminConfig;

  @Before
  public void prepare() throws Exception {
    registerComponentMocks(IModelAccessFacade.class, MainAdminConfig.class);
    mainAdminConfig = getMock(MainAdminConfig.class);
    expect(mainAdminConfig.isAutoLoginEnabled()).andStubReturn(false);
    expect(mainAdminConfig.getXWikiUser())
        .andStubReturn(new XWikiUser(ADMIN_USER_FULL_NAME, true));
    tokenAuthImpl = new TokenLDAPAuthServiceImpl();
    store = getStoreMock();
    expect(getMock(XWiki.class).isVirtualMode()).andReturn(true).anyTimes();
  }

  @Test
  public void test_getUsernameForToken() throws Exception {
    String userToken = "123456789012345678901234";
    Capture<String> captHQL = newCapture();
    Capture<List<?>> captParams = newCapture();
    expect(store.searchDocumentsNames(capture(captHQL), eq(0), eq(0), capture(captParams), same(
        getXContext()))).andReturn(Arrays.asList("Doc.Fullname")).once();

    replayDefault();
    assertEquals("Doc.Fullname", tokenAuthImpl.getUsernameForToken(userToken, getXContext()));
    assertTrue(captHQL.getValue().contains("token.tokenvalue=?"));
    assertTrue("There seems to be no database independent 'now' in hql.",
        captHQL.getValue().contains("token.validuntil>=?"));
    assertTrue(captParams.getValue().contains(tokenAuthImpl.encryptString("hash:SHA-512:",
        userToken)));
    verifyDefault();
  }

  @Test
  public void test_getUsernameForToken_userFromMainwiki() throws Exception {
    String userToken = "123456789012345678901234";
    Capture<String> captHQL = newCapture();
    Capture<String> captHQL2 = newCapture();
    Capture<List<?>> captParams = newCapture();
    expect(store.searchDocumentsNames(capture(captHQL), eq(0), eq(0), capture(captParams), same(
        getXContext()))).andReturn(new ArrayList<>()).once();
    expect(store.searchDocumentsNames(capture(captHQL2), eq(0), eq(0), capture(captParams), same(
        getXContext()))).andReturn(Arrays.asList("Doc.Fullname")).once();

    replayDefault();
    assertEquals("xwiki:Doc.Fullname", tokenAuthImpl.getUsernameForToken(userToken, getXContext()));
    assertTrue(captHQL2.getValue().contains("token.tokenvalue=?"));
    assertTrue("There seems to be no database independent 'now' in hql.",
        captHQL2.getValue().contains("token.validuntil>=?"));
    assertTrue(captParams.getValue().contains(tokenAuthImpl.encryptString("hash:SHA-512:",
        userToken)));
    verifyDefault();
  }

  @Test
  public void test_checkAuthByToken_noUser() throws Exception {
    String userToken = "123456789012345678901234";
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(Collections.emptyList()).times(2);

    replayDefault();
    assertNull(tokenAuthImpl.checkAuthByToken("abcd", userToken, getXContext()));
    verifyDefault();
  }

  @Test
  public void test_checkAuthByToken_admin() throws Exception {
    String userToken = "123456789012345678901234";
    String loginName = "theUserLoginName";
    String username = "XWiki." + loginName;
    List<String> emptyList = Collections.emptyList();
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(emptyList).once();
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(Arrays.asList(username)).once();

    replayDefault();
    XWikiUser loggedInUser = tokenAuthImpl.checkAuthByToken(loginName, userToken, getXContext());
    verifyDefault();
    assertNotNull(loggedInUser);
    String expectedUserName = "xwiki:" + username;
    assertEquals(expectedUserName, loggedInUser.getUser());
    assertEquals(expectedUserName, getXContext().getXWikiUser().getUser());
    assertEquals(expectedUserName, getXContext().getUser());
  }

  @Test
  public void test_checkAuthByToken() throws Exception {
    String userToken = "123456789012345678901234";
    String loginName = "theUserLoginName";
    String username = "XWiki." + loginName;
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(Arrays.asList(username)).once();

    replayDefault();
    assertEquals(username,
        tokenAuthImpl.checkAuthByToken(loginName, userToken, getXContext()).getUser());
    assertEquals(username, getXContext().getXWikiUser().getUser());
    assertEquals(username, getXContext().getUser());
    verifyDefault();
  }

  @Test
  public void test_checkAuthByToken_wrongUserName() throws Exception {
    String userToken = "123456789012345678901234";
    String loginName = "theUserLoginName";
    String username = "XWiki." + loginName;
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(Arrays.asList(username)).once();

    replayDefault();
    assertNull(tokenAuthImpl.checkAuthByToken("abcde", userToken, getXContext()));
    assertNull(getXContext().getXWikiUser());
    assertEquals("XWiki.XWikiGuest", getXContext().getUser());
    verifyDefault();
  }

  @Test
  public void test_checkAuthXWikiContext_noRequest() throws Exception {
    replayDefault();
    assertNull(tokenAuthImpl.checkAuth(getXContext()));
    verifyDefault();
  }

  @Test
  public void test_checkAuthXWikiContext_autoLoginAdmin() throws Exception {
    expect(mainAdminConfig.isAutoLoginEnabled()).andReturn(true);
    expectUserDocument(ADMIN_USER_FULL_NAME);

    replayDefault();
    XWikiUser user = tokenAuthImpl.checkAuth(getXContext());
    verifyDefault();

    assertEquals(ADMIN_USER_FULL_NAME, user.getUser());
    assertTrue(user.isMain());
  }

  @Test
  public void test_checkAuthWithCredentials_autoLoginAdmin_withoutCredentials() throws Exception {
    expect(mainAdminConfig.isAutoLoginEnabled()).andReturn(true);

    replayDefault();
    XWikiUser user = tokenAuthImpl.checkAuth(null, null, null, getXContext());
    verifyDefault();

    assertEquals(ADMIN_USER_FULL_NAME, user.getUser());
    assertTrue(user.isMain());
  }

  @Test
  public void test_authenticate_autoLoginAdmin_withoutCredentials() throws Exception {
    expect(mainAdminConfig.isAutoLoginEnabled()).andReturn(true);

    replayDefault();
    Principal principal = tokenAuthImpl.authenticate(null, null, getXContext());
    verifyDefault();

    assertEquals(ADMIN_USER_FULL_NAME, principal.getName());
  }

  private void expectUserDocument(String fullName) throws Exception {
    DocumentReference userDocRef = getModelUtils().resolveRef(fullName, DocumentReference.class);
    BaseObject userObj = new BaseObject();
    userObj.setDocumentReference(userDocRef);
    userObj.setXClassReference(getBeanFactory()
        .getBean(XWikiUsersClass.CLASS_DEF_HINT, ClassDefinition.class).getClassReference());
    userObj.setIntValue(XWikiUsersClass.FIELD_SUSPENDED.getName(), 0);
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    userDoc.setNew(false);
    userDoc.addXObject(userObj);
    expect(getMock(IModelAccessFacade.class).getDocument(eq(userDocRef))).andReturn(userDoc);
  }

  @Test
  public void test_checkAuthXWikiContext() throws Exception {
    String userToken = "123456789012345678901234";
    String loginName = "theUserLoginName";
    String username = "XWiki." + loginName;
    DocumentReference userDocRef = getModelUtils().resolveRef(username, DocumentReference.class);
    BaseObject userObj = new BaseObject();
    userObj.setDocumentReference(userDocRef);
    userObj.setXClassReference(Utils.getComponent(ClassDefinition.class,
        XWikiUsersClass.CLASS_DEF_HINT).getClassReference());
    userObj.setIntValue(XWikiUsersClass.FIELD_SUSPENDED.getName(), 0);
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    userDoc.setNew(false);
    userDoc.addXObject(userObj);
    XWikiRequest request = createDefaultMock(XWikiRequest.class);
    expect(request.getParameter(eq("token"))).andReturn(userToken).atLeastOnce();
    expect(request.getParameter(eq("username"))).andReturn(loginName).atLeastOnce();
    getXContext().setRequest(request);
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(Arrays.asList(username)).once();
    expect(getMock(IModelAccessFacade.class).getDocument(eq(userDocRef))).andReturn(userDoc);

    replayDefault();
    assertEquals(username, tokenAuthImpl.checkAuth(getXContext()).getUser());
    assertEquals(username, getXContext().getXWikiUser().getUser());
    assertEquals(username, getXContext().getUser());
    verifyDefault();
  }

  @Test
  public void test_checkAuthXWikiContext_noTokenAuth_null() throws Exception {
    XWikiRequest request = createDefaultMock(XWikiRequest.class);
    expect(request.getParameter(eq("token"))).andReturn(null).atLeastOnce();
    expect(request.getParameter(eq("username"))).andReturn(null).atLeastOnce();
    expect(request.getHttpServletRequest()).andReturn(null).anyTimes();
    getXContext().setRequest(request);

    replayDefault();
    assertNull(tokenAuthImpl.checkAuth(getXContext()));
    assertNull(getXContext().getXWikiUser());
    assertEquals("XWiki.XWikiGuest", getXContext().getUser());
    verifyDefault();
  }

  @Test
  public void test_checkAuthXWikiContext_noTokenAuth_emptyString() throws Exception {
    XWikiRequest request = createDefaultMock(XWikiRequest.class);
    expect(request.getParameter(eq("token"))).andReturn("").atLeastOnce();
    expect(request.getParameter(eq("username"))).andReturn("").atLeastOnce();
    expect(request.getHttpServletRequest()).andReturn(null).anyTimes();
    getXContext().setRequest(request);

    replayDefault();
    assertNull(tokenAuthImpl.checkAuth(getXContext()));
    assertNull(getXContext().getXWikiUser());
    assertEquals("XWiki.XWikiGuest", getXContext().getUser());
    verifyDefault();
  }

  @Test
  public void test_checkAuth_suspended() throws Exception {
    String userToken = "123456789012345678901234";
    String loginName = "theUserLoginName";
    String username = "XWiki." + loginName;
    DocumentReference userDocRef = getModelUtils().resolveRef(username, DocumentReference.class);
    BaseObject userObj = new BaseObject();
    userObj.setDocumentReference(userDocRef);
    userObj.setXClassReference(Utils.getComponent(ClassDefinition.class,
        XWikiUsersClass.CLASS_DEF_HINT).getClassReference());
    userObj.setIntValue(XWikiUsersClass.FIELD_SUSPENDED.getName(), 1);
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    userDoc.setNew(false);
    userDoc.addXObject(userObj);
    XWikiRequest request = createDefaultMock(XWikiRequest.class);
    expect(request.getParameter(eq("token"))).andReturn(userToken).atLeastOnce();
    expect(request.getParameter(eq("username"))).andReturn(loginName).atLeastOnce();
    getXContext().setRequest(request);
    expect(store.searchDocumentsNames(anyString(), eq(0), eq(0), anyObject(List.class),
        same(getXContext()))).andReturn(Arrays.asList(username)).once();
    expect(getMock(IModelAccessFacade.class).getDocument(eq(userDocRef))).andReturn(userDoc);

    replayDefault();
    assertNull(tokenAuthImpl.checkAuth(getXContext()));
    assertEquals(username, getXContext().getXWikiUser().getUser());
    assertEquals(username, getXContext().getUser());
    verifyDefault();
  }

}
