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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.collect.ImmutableMap;

// {
// "success": true|false,
// "challenge_ts": timestamp, // timestamp of the challenge load (ISO format
// yyyy-MM-dd'T'HH:mm:ssZZ)
// "hostname": string, // the hostname of the site where the reCAPTCHA was solved
// "error-codes": [...] // optional
// }
public class ReCaptchaResponse {

  public static final Map<String, String> ERROR_MESSAGES = new ImmutableMap.Builder<String, String>()
      .put("missing-input-secret", "The secret parameter is missing.")
      .put("invalid-input-secret", "The secret parameter is invalid or malformed.")
      .put("missing-input-response", "The response parameter is missing.")
      .put("invalid-input-response", "The response parameter is invalid or malformed.")
      .put("bad-request", "The request is invalid or malformed.")
      .put("timeout-or-duplicate", "The response is no longer valid: either is too old or has "
          + "been used previously.")
      .build();

  private boolean success;
  private Instant timestamp;
  private String hostname;
  private List<String> errorCodes;

  public ReCaptchaResponse(@JsonProperty("success") boolean success,
      @JsonProperty("challenge_ts") String timestamp,
      @JsonProperty("hostname") String hostname,
      @JsonProperty("error-codes") String[] errors) {
    setSuccess(success);
    setTimestamp(timestamp);
    setHostname(hostname);
    setErrorCodes(errors);
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  // ISO format yyyy-MM-dd'T'HH:mm:ssZZ
  public void setTimestamp(String timestamp) {
    if (timestamp != null) {
      this.timestamp = DateTimeFormatter.ISO_INSTANT.parse(timestamp, Instant::from);
    }
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public List<String> getErrorCodes() {
    return errorCodes;
  }

  public void setErrorCodes(String[] errorCodes) {
    this.errorCodes = ((errorCodes != null) ? List.of(errorCodes) : List.of());
  }
}
