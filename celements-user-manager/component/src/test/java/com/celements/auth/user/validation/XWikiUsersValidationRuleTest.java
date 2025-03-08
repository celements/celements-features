package com.celements.auth.user.validation;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;

import com.celements.auth.user.User;
import com.celements.auth.user.UserService;
import com.celements.common.test.AbstractComponentTest;
import com.celements.docform.DocFormRequestKey;
import com.celements.docform.DocFormRequestParam;
import com.celements.mailsender.IMailSenderRole;
import com.celements.model.reference.RefBuilder;
import com.celements.rights.access.EAccessLevel;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.celements.validation.ValidationResult;
import com.celements.validation.ValidationType;
import com.celements.web.classes.oldcore.XWikiUsersClass;

public class XWikiUsersValidationRuleTest extends AbstractComponentTest {

  private XWikiUsersValidationRule rule;
  private DocumentReference userDocRef1 = new DocumentReference("wiki", "XWiki", "321adfawer");
  private DocumentReference userDocRef2 = new DocumentReference("wiki", "XWiki", "a65e4rafgoij");
  private String correctEmail = "abc+test@synventis.com";

  @Before
  public void prepare() throws Exception {
    registerComponentMocks(IMailSenderRole.class,
        UserService.class,
        User.class,
        IRightsAccessFacadeRole.class);
    rule = getBeanFactory().getBean(XWikiUsersValidationRule.class);
  }

  @Test
  public void test_validate_noXWikiUsersInRequest() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(new DocFormRequestParam(DocFormRequestKey.createObjFieldKey(
        "XWiki.blabla_0_test",
        new DocumentReference("wiki", "bellis", "aoiueelkt34q53u"),
        new RefBuilder().space("XWiki").doc("blabla").build(ClassReference.class),
        0,
        "test"),
        List.of("adflkj")));

    replayDefault();
    List<ValidationResult> result = rule.validate(params);
    verifyDefault();

    assertTrue(result.isEmpty());
  }

  @Test
  public void test_validate_differentUserDocRefsInRequest() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(createEmailParam(correctEmail, 0, userDocRef1));
    params.add(createEmailParam(correctEmail, 0, userDocRef2));

    replayDefault();
    List<ValidationResult> result = rule.validate(params);
    verifyDefault();

    assertEquals(1, result.size());
    assertEquals(ValidationType.ERROR, result.get(0).getType());
    assertEquals("cel_useradmin_invalidRequest", result.get(0).getMessage());
  }

  @Test
  public void test_validate_differentUserObjInRequest() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(createEmailParam(correctEmail, 0, userDocRef1));
    params.add(createEmailParam(correctEmail, 1, userDocRef1));

    replayDefault();
    List<ValidationResult> result = rule.validate(params);
    verifyDefault();

    assertEquals(1, result.size());
    assertEquals(ValidationType.ERROR, result.get(0).getType());
    assertEquals("cel_useradmin_invalidRequest", result.get(0).getMessage());
  }

  @Test
  public void test_validate_emailParamWithSeveralValues() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(new DocFormRequestParam(DocFormRequestKey.createObjFieldKey(
        "XWiki.XWikiUsers_0_email",
        userDocRef1,
        XWikiUsersClass.CLASS_REF,
        0,
        "email"),
        List.of("aerölkja@synventis.com", "öliulk@synventis.com")));

    replayDefault();
    List<ValidationResult> results = rule.validate(params);
    verifyDefault();

    assertEquals(1, results.size());
    assertEquals(ValidationType.ERROR, results.get(0).getType());
    assertEquals("cel_useradmin_invalidRequest", results.get(0).getMessage());
  }

  @Test
  public void test_validate_wrongSpace() {
    DocumentReference wrongUserDocRef = new RefBuilder().wiki("wiki").space("bla").doc("adju34n35n")
        .build(DocumentReference.class);
    DocFormRequestParam emailParam = createEmailParam(correctEmail, 0, wrongUserDocRef);

    replayDefault();
    List<ValidationResult> result = rule.validate(List.of(emailParam));
    verifyDefault();

    assertEquals(1, result.size());
    assertEquals(ValidationType.ERROR, result.get(0).getType());
    assertEquals("cel_useradmin_invalidRequest", result.get(0).getMessage());
  }

  @Test
  public void test_checkEmailValidity_noEmailParam() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(new DocFormRequestParam(DocFormRequestKey.createObjFieldKey(
        "XWiki.XWikiUsers_0_name",
        userDocRef1,
        XWikiUsersClass.CLASS_REF,
        0,
        "name"),
        List.of("adflkj")));

    replayDefault();
    Optional<ValidationResult> result = rule.checkEmailValidity(params);
    verifyDefault();

    assertTrue(result.isPresent());
    assertEquals(ValidationType.ERROR, result.get().getType());
    assertEquals("cel_useradmin_missingEmail", result.get().getMessage());
  }

  @Test
  public void test_checkEmailValidity_emailParamWithEmptyValues() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(new DocFormRequestParam(DocFormRequestKey.createObjFieldKey(
        "XWiki.XWikiUsers_0_email",
        userDocRef1,
        XWikiUsersClass.CLASS_REF,
        0,
        "email"),
        List.of()));

    replayDefault();
    Optional<ValidationResult> result = rule.checkEmailValidity(params);
    verifyDefault();

    assertTrue(result.isPresent());
    assertEquals(ValidationType.ERROR, result.get().getType());
    assertEquals("cel_useradmin_missingEmail", result.get().getMessage());
  }

  @Test
  public void test_checkEmailValidity_invalidEmail() {
    List<DocFormRequestParam> params = List.of(createEmailParam("abc", 0, userDocRef1));
    expect(getMock(IMailSenderRole.class).isValidEmail("abc")).andReturn(false);

    replayDefault();
    Optional<ValidationResult> result = rule.checkEmailValidity(params);
    verifyDefault();

    assertTrue(result.isPresent());
    assertEquals(ValidationType.ERROR, result.get().getType());
    assertEquals("cel_useradmin_emailInvalid", result.get().getMessage());
  }

  @Test
  public void test_checkUniqueEmail_emailNotUnique() {
    DocFormRequestParam emailParam = createEmailParam(correctEmail, 0, userDocRef1);
    User user = createDefaultMock(User.class);
    expect(getMock(UserService.class).getPossibleLoginFields()).andReturn(new HashSet<String>());
    expect(getMock(UserService.class)
        .getPossibleUserForLoginField("abc+test@synventis.com", new HashSet<String>()))
            .andReturn(Optional.of(user));
    expect(user.getDocRef()).andReturn(userDocRef2);

    replayDefault();
    Optional<ValidationResult> result = rule.checkUniqueEmail(correctEmail, emailParam);
    verifyDefault();

    assertTrue(result.isPresent());
    assertEquals(ValidationType.ERROR, result.get().getType());
    assertEquals("cel_useradmin_emailNotUnique", result.get().getMessage());
  }

  @Test
  public void test_validate_allOk() {
    List<DocFormRequestParam> params = new ArrayList<>();
    params.add(createEmailParam(correctEmail, 0, userDocRef1));
    expect(getMock(IMailSenderRole.class).isValidEmail("abc+test@synventis.com")).andReturn(true);
    expect(getMock(UserService.class).getPossibleLoginFields()).andReturn(new HashSet<String>());
    expect(getMock(UserService.class)
        .getPossibleUserForLoginField(correctEmail, new HashSet<String>()))
            .andReturn(Optional.empty());

    replayDefault();
    List<ValidationResult> results = rule.validate(params);
    verifyDefault();

    assertEquals(0, results.size());
  }

  @Test
  public void test_checkRegisterAccessRights_noRights() {
    List<DocFormRequestParam> params = List.of(createEmailParam(correctEmail, -1, userDocRef1));
    expect(getMock(IRightsAccessFacadeRole.class).isAdmin()).andReturn(false);
    expect(getMock(IRightsAccessFacadeRole.class)
        .hasAccessLevel(params.get(0).getDocRef(), EAccessLevel.REGISTER)).andReturn(false);

    replayDefault();
    Optional<ValidationResult> result = rule.checkRegisterAccessRights(params);
    verifyDefault();

    assertTrue(result.isPresent());
    assertEquals(ValidationType.ERROR, result.get().getType());
    assertEquals("cel_useradmin_noRegisterRights", result.get().getMessage());
  }

  private DocFormRequestParam createEmailParam(String email, int objNb,
      DocumentReference userDocRef) {
    DocFormRequestParam emailParam = new DocFormRequestParam(DocFormRequestKey.createObjFieldKey(
        "XWiki.XWikiUsers_" + objNb + "_email",
        userDocRef,
        XWikiUsersClass.CLASS_REF,
        objNb,
        "email"),
        List.of(email));
    return emailParam;

  }

}
