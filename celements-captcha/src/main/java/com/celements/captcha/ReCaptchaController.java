package com.celements.captcha;

import javax.inject.Inject;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.celements.spring.security.AuthenticatedBaseController;
import com.google.common.base.Strings;

@RestController
@RequestMapping("/v1/recaptcha")
@PreAuthorize("permitAll()")
public class ReCaptchaController extends AuthenticatedBaseController {

  private final ReCaptchaService reCaptcha;

  @Inject
  public ReCaptchaController(ReCaptchaService reCaptcha) {
    this.reCaptcha = reCaptcha;
  }

  @PostMapping(
      value = "/verify",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ReCaptchaResponse verify(
      @RequestBody VerifyRequest request) {
    if (checkAuth().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    if ((request == null) || Strings.isNullOrEmpty(request.token())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing reCAPTCHA token");
    }
    return reCaptcha.verify(request.token(), null)
        .orElseThrow(() -> new IllegalStateException("Failed verifying reCaptcha response"));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> handleException(IllegalStateException e) {
    return toErrorResponse(HttpStatus.NOT_ACCEPTABLE, e);
  }

  public record VerifyRequest(String token) {}

}
