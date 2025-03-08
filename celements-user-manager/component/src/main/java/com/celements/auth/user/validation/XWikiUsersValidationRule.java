package com.celements.auth.user.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Component;

import com.celements.auth.user.User;
import com.celements.auth.user.UserService;
import com.celements.docform.DocFormRequestKey;
import com.celements.docform.DocFormRequestParam;
import com.celements.mailsender.IMailSenderRole;
import com.celements.rights.access.EAccessLevel;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.celements.validation.IRequestValidationRule;
import com.celements.validation.ValidationResult;
import com.celements.validation.ValidationType;
import com.celements.web.classes.oldcore.XWikiUsersClass;
import com.xpn.xwiki.XWikiConstant;

/**
 * This class validates params with the ClassReference XWiki.XWikiUsers. It only handles one
 * user per request and one email per user. Other requests will be invalid. An email may not be
 * null, nor an empty String.
 *
 * @author cpichler
 */
@Component
public class XWikiUsersValidationRule implements IRequestValidationRule {

  private static final ValidationResult INVALID_REQUEST = new ValidationResult(ValidationType.ERROR,
      "invalid request", "cel_useradmin_invalidRequest");
  private static final ValidationResult INVALID_EMAIL = new ValidationResult(ValidationType.ERROR,
      "invalid email", "cel_useradmin_emailInvalid");
  private static final ValidationResult MISSING_EMAIL = new ValidationResult(ValidationType.ERROR,
      "missing email", "cel_useradmin_missingEmail");
  private static final ValidationResult EMAIL_NOT_UNIQUE = new ValidationResult(
      ValidationType.ERROR, "email not unique", "cel_useradmin_emailNotUnique");
  private static final ValidationResult NO_REGISTER_RIGHTS = new ValidationResult(
      ValidationType.ERROR, "no register rights", "cel_useradmin_noRegisterRights");

  private final IMailSenderRole mailSenderService;
  private final UserService userService;
  private final IRightsAccessFacadeRole rightsAccess;

  @Inject
  public XWikiUsersValidationRule(
      IMailSenderRole mailSenderService,
      UserService userService,
      IRightsAccessFacadeRole rightsAccess) {
    this.mailSenderService = mailSenderService;
    this.userService = userService;
    this.rightsAccess = rightsAccess;
  }

  @Override
  public @NotNull List<ValidationResult> validate(@NotNull List<DocFormRequestParam> params) {
    List<DocFormRequestParam> paramsToValidate = findParamsWithXWikiUsersClassRef(params);
    if (paramsToValidate.isEmpty()) {
      return List.of();
    }
    if (!isRequestInvalid(paramsToValidate)) {
      return validateParams(paramsToValidate);
    }
    return List.of(INVALID_REQUEST);
  }

  private boolean isRequestInvalid(List<DocFormRequestParam> paramsToValidate) {
    return !isSameUser(paramsToValidate)
        || hasSeveralEmails(paramsToValidate)
        || isNotXWikiSpace(paramsToValidate);
  }

  private boolean isSameUser(List<DocFormRequestParam> params) {
    return hasSameDocRef(params) && hasSameObjNb(params);
  }

  private boolean hasSameDocRef(List<DocFormRequestParam> params) {
    return params.stream().map(p -> p.getDocRef()).distinct().count() == 1;
  }

  private boolean hasSameObjNb(List<DocFormRequestParam> params) {
    return params.stream().map(p -> p.getKey().getObjNb()).distinct().count() == 1;
  }

  private boolean hasSeveralEmails(List<DocFormRequestParam> params) {
    return getEmailParams(params).flatMap(p -> p.getValues().stream()).count() > 1;
  }

  private boolean isNotXWikiSpace(List<DocFormRequestParam> params) {
    DocFormRequestParam param = params.get(0);
    return !param.getDocRef().getLastSpaceReference().getName().equals(XWikiConstant.XWIKI_SPACE);
  }

  private List<ValidationResult> validateParams(List<DocFormRequestParam> params) {
    List<ValidationResult> validationResults = new ArrayList<>();
    checkEmailValidity(params).ifPresent(validationResults::add);
    checkRegisterAccessRights(params).ifPresent(validationResults::add);
    return validationResults;
  }

  Optional<ValidationResult> checkEmailValidity(List<DocFormRequestParam> params) {
    Optional<DocFormRequestParam> emailParam = getEmailParams(params).findAny();
    // DocFormRequestParam turns null values into empty Strings and deletes those from the list of
    // values. You should always get a list but it might be empty.
    if (emailParam.isEmpty() || emailParam.get().getValues().isEmpty()) {
      return Optional.of(MISSING_EMAIL);
    }
    String email = emailParam.get().getValues().get(0);
    if (!mailSenderService.isValidEmail(email)) {
      return Optional.of(INVALID_EMAIL);
    }
    return checkUniqueEmail(email, emailParam.get());
  }

  Optional<ValidationResult> checkUniqueEmail(String email,
      DocFormRequestParam emailParam) {
    Optional<User> user = userService.getPossibleUserForLoginField(email,
        userService.getPossibleLoginFields());
    if (user.isEmpty() || user.get().getDocRef().equals(emailParam.getDocRef())) {
      return Optional.empty();
    }
    return Optional.of(EMAIL_NOT_UNIQUE);
  }

  Optional<ValidationResult> checkRegisterAccessRights(List<DocFormRequestParam> params) {
    DocFormRequestParam param = params.get(0);
    if (isNotNewUser(param)
        || rightsAccess.isAdmin()
        || rightsAccess.hasAccessLevel(param.getDocRef(), EAccessLevel.REGISTER)) {
      return Optional.empty();
    }
    return Optional.of(NO_REGISTER_RIGHTS);
  }

  private boolean isNotNewUser(DocFormRequestParam param) {
    return param.getKey().getObjNb() != -1;
  }

  private List<DocFormRequestParam> findParamsWithXWikiUsersClassRef(
      List<DocFormRequestParam> params) {
    return params.stream()
        .filter(p -> p.getKey().getType().equals(DocFormRequestKey.Type.OBJ_FIELD))
        .filter(p -> p.getKey().getClassRef().equals(XWikiUsersClass.CLASS_REF))
        .collect(Collectors.toList());
  }

  private Stream<DocFormRequestParam> getEmailParams(List<DocFormRequestParam> params) {
    return params.stream().filter(p -> p.getKey().getFieldName().equals("email"));
  }

}
