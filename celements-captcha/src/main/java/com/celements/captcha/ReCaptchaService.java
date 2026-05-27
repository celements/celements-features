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
package com.celements.captcha;

import static com.google.common.base.Strings.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.configuration.ConfigurationSource;

import com.celements.model.context.ModelContext;

@Component("reCaptcha")
public class ReCaptchaService implements CaptchaServiceRole {

  private static final Logger LOGGER = LoggerFactory.getLogger(ReCaptchaService.class);

  // Google ReCaptcha URL and params
  private static final String CFG_RECAPTCHA_VALIDATION_URL = "https://www.google.com/recaptcha/api/siteverify?";
  private static final String CFG_RECAPTCHA_VALIDATION_URL_PARAM_CAPTCHA = "&response=";
  private static final String CFG_RECAPTCHA_VALIDATION_URL_PARAM_CLIENTIP = "&remoteip=";
  private static final String CFG_RECAPTCHA_VALIDATION_URL_PARAM_SECRET = "&secret=";

  private static final String CFG_KEY_RECAPTCHA_SECRET = "reCaptchaServerSecret";
  private static final String CFG_FORM_FIELD_KEY = "g-recaptcha-response";

  @Requirement
  private ModelContext context;

  @Requirement
  private ConfigurationSource configSource;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Optional<ReCaptchaResponse> verify() {
    return context.getRequestParam(CFG_FORM_FIELD_KEY)
        .flatMap(captcha -> verify(captcha, getClientIp()));
  }

  @Override
  public Optional<ReCaptchaResponse> verify(String captcha, String clientIp) {
    String secret = configSource.getProperty(CFG_KEY_RECAPTCHA_SECRET, "");
    if (isNullOrEmpty(secret) || isNullOrEmpty(captcha)) {
      return Optional.empty();
    }
    String urlString = CFG_RECAPTCHA_VALIDATION_URL
        + CFG_RECAPTCHA_VALIDATION_URL_PARAM_CAPTCHA + encode(captcha)
        + CFG_RECAPTCHA_VALIDATION_URL_PARAM_CLIENTIP + encode(clientIp)
        + CFG_RECAPTCHA_VALIDATION_URL_PARAM_SECRET + encode(secret);
    try {
      var url = new URI(urlString).toURL();
      var response = objectMapper.readValue(url, ReCaptchaResponse.class);
      return Optional.ofNullable(response);
    } catch (URISyntaxException | IOException e) {
      LOGGER.error("Error verifying reCaptcha response", e);
    }
    return Optional.empty();
  }

  private String getClientIp() {
    var request = context.request().orElseThrow(IllegalStateException::new);
    String ip = request.getHeader("X-FORWARDED-FOR");
    return isNullOrEmpty(ip) ? request.getRemoteAddr() : ip;
  }

  private String encode(String value) {
    return URLEncoder.encode(nullToEmpty(value), StandardCharsets.UTF_8);
  }

}
