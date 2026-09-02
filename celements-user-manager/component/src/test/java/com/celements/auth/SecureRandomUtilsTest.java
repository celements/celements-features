package com.celements.auth;

import static org.junit.Assert.*;

import org.junit.Test;

public class SecureRandomUtilsTest {

  @Test
  public void test_randomAlphanumeric() {
    assertEquals("", SecureRandomUtils.randomAlphanumeric(0));
    assertTrue(SecureRandomUtils.randomAlphanumeric(24).matches("[A-Za-z0-9]{24}"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void test_randomAlphanumeric_negativeLength() {
    SecureRandomUtils.randomAlphanumeric(-1);
  }
}
