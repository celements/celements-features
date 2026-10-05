package com.celements.mandatory;

import static com.xpn.xwiki.XWikiConstant.*;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;

import com.celements.auth.AuthenticationService;
import com.celements.auth.MainAdminConfig;
import com.celements.auth.SecureRandomUtils;
import com.celements.auth.user.UserInstantiationException;
import com.celements.auth.user.UserService;
import com.celements.model.access.exception.DocumentSaveException;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

@Component("celements.mandatory.MainAdminUser")
public class MainAdminUser extends AbstractMandatoryDocument {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainAdminUser.class);

  @Inject
  private UserService userService;

  @Inject
  private AuthenticationService authService;

  @Inject
  private MainAdminConfig config;

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
    return config.getUserDocRef();
  }

  @Override
  protected boolean isEnabledByDefault() {
    return config.isAutoLoginEnabled() || config.getPassword().isPresent();
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
      var adminUser = userService.getUser(config.getUserDocRef());
      if (isEnabledByDefault()) {
        var password = config.getPassword().orElseGet(() -> SecureRandomUtils.randomAlphanumeric(24));
        authService.enableUser(adminUser, password, false);
      }
      userService.addUserToGroup(adminUser, getAdminGroupRef());
      return false; // safe already handled
    } catch (UserInstantiationException | DocumentSaveException exc) {
      throw new XWikiException(0, 0, "Admin user document not found", exc);
    }
  }

  private ClassReference getAdminGroupRef() {
    return new ClassReference(XWIKI_SPACE, "XWikiAdminGroup");
  }

  @Override
  public Logger getLogger() {
    return LOGGER;
  }

}
