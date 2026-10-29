package com.celements.auth;

import static com.google.common.base.Preconditions.*;
import static com.xpn.xwiki.XWikiConstant.*;
import static java.util.function.Predicate.*;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.context.annotation.Configuration;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.reference.DocumentReference;

import com.celements.model.util.ModelUtils;
import com.celements.servlet.NodeConfig.NodeIdentity;
import com.xpn.xwiki.user.api.XWikiUser;

@Configuration
public class MainAdminConfig {

  static final String CFG_KEY_AUTOLOGIN = "main.admin.autologin";
  static final String CFG_KEY_PASSWORD = "main.admin.password";

  public static final DocumentReference USER_DOC_REF =
      new DocumentReference(MAIN_WIKI.getName(), XWIKI_SPACE, "Admin");

  private final ConfigurationSource configSource;
  private final NodeIdentity nodeIdentity;
  private final ModelUtils modelUtils;

  @Inject
  public MainAdminConfig(
      @Named("xwikiproperties") ConfigurationSource configSource,
      NodeIdentity nodeIdentity,
      ModelUtils modelUtils) {
    this.configSource = configSource;
    this.nodeIdentity = nodeIdentity;
    this.modelUtils = modelUtils;
    isAutoLoginEnabled();
  }

  public DocumentReference getUserDocRef() {
    return USER_DOC_REF;
  }

  public XWikiUser getXWikiUser() {
    return new XWikiUser(modelUtils.serializeRef(USER_DOC_REF), true);
  }

  public Optional<String> getPassword() {
    return Optional.ofNullable(configSource.getProperty(CFG_KEY_PASSWORD, ""))
        .map(String::trim)
        .filter(not(String::isEmpty));
  }

  public boolean isAutoLoginEnabled() {
    var enabled = configSource.getProperty(CFG_KEY_AUTOLOGIN, false);
    checkState(!enabled || "local".equals(nodeIdentity.clusterName()),
        "Admin auto-login requires CLUSTER_NAME=local");
    return enabled;
  }
}
