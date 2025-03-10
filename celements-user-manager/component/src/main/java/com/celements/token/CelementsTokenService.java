package com.celements.token;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.QueryException;

import com.celements.auth.IAuthenticationServiceRole;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.access.exception.DocumentSaveException;
import com.celements.model.context.ModelContext;
import com.celements.model.object.xwiki.XWikiObjectEditor;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

@Component
public class CelementsTokenService implements TokenService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CelementsTokenService.class);

  private final IModelAccessFacade modelAccess;
  private final IAuthenticationServiceRole authService;
  private final ModelContext context;

  public CelementsTokenService(
      IModelAccessFacade modelAccess,
      IAuthenticationServiceRole authService,
      ModelContext context) {
    this.modelAccess = modelAccess;
    this.authService = authService;
    this.context = context;
  }

  @Override
  public Optional<String> addNewTokenToDocument(DocumentReference tokenDocRef, int minutesValid) {
    XWikiDocument tokenDoc;
    try {
      tokenDoc = modelAccess.getDocument(tokenDocRef);
      return addNewTokenToDocument(tokenDoc, minutesValid);
    } catch (DocumentNotExistsException exp) {
      LOGGER.error("Failed to add new token to document {}", tokenDocRef, exp);
    }
    return Optional.empty();
  }

  @Override
  public Optional<String> addNewTokenToDocument(XWikiDocument tokenDoc, int minutesValid) {
    try {
      removeOutdatedTokens(tokenDoc);
      String validkey = createTokenObject(tokenDoc, minutesValid);
      modelAccess.saveDocument(tokenDoc);
      LOGGER.debug("addNewTokenToDocument - sucessfully created token for user [{}]",
          tokenDoc.getDocumentReference());
      return Optional.of(validkey);
    } catch (QueryException | DocumentSaveException exp) {
      LOGGER.error("add new token to document {} failed.", tokenDoc.getDocumentReference(), exp);
    }
    return Optional.empty();
  }

  private String createTokenObject(XWikiDocument tokenDoc, int minutesValid) throws QueryException {
    // XXX doesn't guarantee a unique key regarding tokens
    String validkey = authService.getUniqueValidationKey();
    BaseObject obj = XWikiObjectEditor.on(tokenDoc).filter(getTokenClassRef()).createFirst();
    obj.set("tokenvalue", validkey, context.getXWikiContext());
    Calendar myCal = Calendar.getInstance();
    myCal.add(Calendar.MINUTE, minutesValid);
    obj.setDateValue("validuntil", myCal.getTime());
    return validkey;
  }

  synchronized boolean removeOutdatedTokens(XWikiDocument tokenDoc) {
    LOGGER.trace("removeOutdatedTokens - {}", tokenDoc.getDocumentReference());
    boolean changed = false;
    Date now = new Date();
    for (BaseObject obj : XWikiObjectFetcher.on(tokenDoc).filter(getTokenClassRef()).iter()) {
      Date validUntil = obj.getDateValue("validuntil");
      if ((validUntil == null) || validUntil.before(now)) {
        LOGGER.trace("removeOutdatedTokens - deleting [{}]", obj);
        changed |= tokenDoc.removeXObject(obj);
      }
    }
    return changed;
  }

  @Override
  public ClassReference getTokenClassRef() {
    return new ClassReference("Classes", "TokenClass");
  }

}
