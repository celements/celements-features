package com.celements.auth;

import static com.celements.auth.MainAdminConfig.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.util.ModelUtils;
import com.celements.servlet.NodeConfig.NodeIdentity;
import com.xpn.xwiki.user.api.XWikiUser;

public class MainAdminConfigTest extends AbstractComponentTest {

  @Before
  public void prepareTest() throws Exception {
    registerComponentMocks(NodeIdentity.class, ModelUtils.class);
  }

  @Test
  public void test_adminUserIdentity() {
    expect(getMock(ModelUtils.class).serializeRef(same(USER_DOC_REF)))
        .andReturn("xwiki:XWiki.Admin");

    replayDefault();
    MainAdminConfig config = getBeanFactory().getBean(MainAdminConfig.class);
    XWikiUser user = config.getXWikiUser();
    verifyDefault();

    assertSame(USER_DOC_REF, config.getUserDocRef());
    assertEquals("xwiki:XWiki.Admin", user.getUser());
    assertTrue(user.isMain());
  }

  @Test
  public void test_getPassword_trimmed() {
    getConfigurationSource().setProperty(CFG_KEY_PASSWORD, " password ");

    replayDefault();
    MainAdminConfig config = getBeanFactory().getBean(MainAdminConfig.class);
    verifyDefault();

    assertEquals("password", config.getPassword().orElseThrow());
  }

  @Test
  public void test_getPassword_blank() {
    getConfigurationSource().setProperty(CFG_KEY_PASSWORD, " ");

    replayDefault();
    MainAdminConfig config = getBeanFactory().getBean(MainAdminConfig.class);
    verifyDefault();

    assertTrue(config.getPassword().isEmpty());
  }

  @Test
  public void test_isAutoLoginEnabled_localCluster() {
    getConfigurationSource().setProperty(CFG_KEY_AUTOLOGIN, true);
    expect(getMock(NodeIdentity.class).clusterName()).andReturn("local").times(2);

    replayDefault();
    MainAdminConfig config = getBeanFactory().getBean(MainAdminConfig.class);
    assertTrue(config.isAutoLoginEnabled());
    verifyDefault();
  }

  @Test
  public void test_constructor_autoLoginRejectsNonLocalCluster() {
    getConfigurationSource().setProperty(CFG_KEY_AUTOLOGIN, true);
    expect(getMock(NodeIdentity.class).clusterName()).andReturn("production");

    replayDefault();
    BeanCreationException exc = assertThrows(BeanCreationException.class,
        () -> getBeanFactory().getBean(MainAdminConfig.class));
    verifyDefault();

    assertEquals("Admin auto-login requires CLUSTER_NAME=local",
        exc.getMostSpecificCause().getMessage());
  }
}
