package com.celements.auth;

import java.security.SecureRandom;

import org.apache.commons.lang.RandomStringUtils;

public final class SecureRandomUtils {

  private static final SecureRandom RANDOM = new SecureRandom();

  private SecureRandomUtils() {}

  public static String randomAlphanumeric(int length) {
    return RandomStringUtils.random(length, 0, 0, true, true, null, RANDOM);
  }
}
