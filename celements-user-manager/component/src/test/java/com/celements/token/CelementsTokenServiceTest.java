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
package com.celements.token;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.QueryException;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.web.Utils;

public class CelementsTokenServiceTest extends AbstractComponentTest {

  private CelementsTokenService tokenService;

  @Before
  public void setUp_NewCelementsTokenForUserCommandTest() throws Exception {
    tokenService = (CelementsTokenService) Utils.getComponent(TokenService.class);
  }

  @Test
  public void test_removeOutdatedTokens_noTokens() throws QueryException {
    DocumentReference userDocRef = new DocumentReference("db", "XWiki", "User");
    XWikiDocument userDoc = new XWikiDocument(userDocRef);

    replayDefault();
    assertFalse(tokenService.removeOutdatedTokens(userDoc));
    verifyDefault();
    assertFalse(getTokenObjects(userDoc).exists());
  }

  @Test
  public void test_removeOutdatedTokens_1new() throws QueryException {
    DocumentReference userDocRef = new DocumentReference("db", "XWiki", "User");
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    Date afterNow = new Date();
    afterNow.setTime(afterNow.getTime() + 1000000l);
    createTokenObject(userDoc, afterNow);

    replayDefault();
    assertFalse(tokenService.removeOutdatedTokens(userDoc));
    verifyDefault();
    assertEquals(1, getTokenObjects(userDoc).count());
  }

  @Test
  public void test_removeOutdatedTokens_1outdated() throws QueryException {
    DocumentReference userDocRef = new DocumentReference("db", "XWiki", "User");
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    Date beforeNow = new Date();
    beforeNow.setTime(beforeNow.getTime() - 1000000l);
    createTokenObject(userDoc, beforeNow);

    replayDefault();
    assertTrue(tokenService.removeOutdatedTokens(userDoc));
    verifyDefault();
    assertFalse(getTokenObjects(userDoc).exists());
  }

  @Test
  public void test_removeOutdatedTokens_multiple() throws QueryException {
    DocumentReference userDocRef = new DocumentReference("db", "XWiki", "User");
    XWikiDocument userDoc = new XWikiDocument(userDocRef);
    Date afterNow = new Date();
    afterNow.setTime(afterNow.getTime() + 1000000l);
    createTokenObject(userDoc, afterNow);
    Date beforeNow = new Date();
    beforeNow.setTime(beforeNow.getTime() - 1000000l);
    createTokenObject(userDoc, beforeNow);
    beforeNow.setTime(beforeNow.getTime() - 1000000l);
    createTokenObject(userDoc, beforeNow);

    replayDefault();
    assertTrue(tokenService.removeOutdatedTokens(userDoc));
    verifyDefault();
    assertEquals(1, getTokenObjects(userDoc).count());
    assertEquals(afterNow, getTokenObjects(userDoc).first().get().getDateValue("validuntil"));
  }

  private BaseObject createTokenObject(XWikiDocument doc, Date validUntil) {
    BaseObject obj = new BaseObject();
    obj.setXClassReference(tokenService.getTokenClassRef().getDocRef(
        doc.getDocumentReference().getWikiReference()));
    obj.setDateValue("validuntil", validUntil);
    doc.addXObject(obj);
    return obj;
  }

  private XWikiObjectFetcher getTokenObjects(XWikiDocument doc) {
    return XWikiObjectFetcher.on(doc).filter(tokenService.getTokenClassRef());
  }

}
