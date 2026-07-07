package com.celements.mandatory;

import static com.celements.common.lambda.LambdaExceptionUtil.*;
import static java.util.function.Predicate.*;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;

import com.celements.auth.AuthenticationService;
import com.celements.auth.user.UserInstantiationException;
import com.celements.auth.user.UserService;
import com.celements.model.access.exception.DocumentSaveException;
import com.celements.model.reference.RefBuilder;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

@Component("celements.mandatory.MainAdminUser")
public class MainAdminUser extends AbstractMandatoryDocument {

  public static final String ADMIN_DOC_NAME = "Admin";
  public static final String CFG_KEY_ADMIN_PASSWORD = "main.admin.password";

  private static final Logger LOGGER = LoggerFactory.getLogger(MainAdminUser.class);

  @Inject
  private UserService userService;

  @Inject
  private AuthenticationService authService;

  @Override
  public String getName() {
    return "MainAdminUser";
  }

  @Override
  public List<String> dependsOnMandatoryDocuments() {
    return List.of(
        "celements.MandatoryGroups", // Admin group must exist
        MandatoryDiskDocuments.class.getName()); // Admin user must exist, XWiki/Admin.xml
  }

  @Override
  protected DocumentReference getDocRef() {
    return getAdminUserDocRef();
  }

  @Override
  protected boolean isEnabledByDefault() {
    return getAdminPassword().isPresent();
  }

  @Override
  protected boolean skip() {
    return !modelUtils.isMainWiki(modelContext.getWikiRef());
  }

  @Override
  protected boolean checkDocuments(XWikiDocument doc) throws XWikiException {
    return false;
  }

  @Override
  protected boolean checkDocumentsMain(XWikiDocument doc) throws XWikiException {
    try {
      var adminUser = userService.getUser(getAdminUserDocRef());
      getAdminPassword().ifPresent(rethrowConsumer(password -> authService
          .enableUser(adminUser, password, false)));
      userService.addUserToGroup(adminUser, getAdminGroupRef());
      return false; // safe already handled
    } catch (UserInstantiationException | DocumentSaveException exc) {
      throw new XWikiException(0, 0, "Admin user document not found", exc);
    }
  }

  Optional<String> getAdminPassword() {
    String password = xwikiPropConfigSource.getProperty(CFG_KEY_ADMIN_PASSWORD, "");
    return Optional.ofNullable(password).map(String::trim).filter(not(String::isEmpty));
  }

  private DocumentReference getAdminUserDocRef() {
    return new RefBuilder()
        .with(modelContext.getWikiRef())
        .space(XWikiConstant.XWIKI_SPACE)
        .doc(ADMIN_DOC_NAME)
        .build(DocumentReference.class);
  }

  private ClassReference getAdminGroupRef() {
    return new ClassReference(XWikiConstant.XWIKI_SPACE, "XWikiAdminGroup");
  }

  @Override
  public Logger getLogger() {
    return LOGGER;
  }

}
