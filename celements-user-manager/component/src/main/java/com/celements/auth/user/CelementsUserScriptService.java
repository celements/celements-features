package com.celements.auth.user;

import java.util.Set;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Component;
import org.xwiki.script.service.ScriptService;

import com.google.common.base.Joiner;

@Component("user")
public class CelementsUserScriptService implements ScriptService {

  private final UserService userService;

  @Inject
  public CelementsUserScriptService(UserService userService) {
    this.userService = userService;
  }

  public @NotNull Set<String> getPossibleLoginsAsSet() {
    return userService.getPossibleLoginFields();
  }

  public String getPossibleLogins() {
    return Joiner.on(',').join(userService.getPossibleLoginFields());
  }

}
