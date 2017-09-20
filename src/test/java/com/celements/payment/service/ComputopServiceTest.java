package com.celements.payment.service;

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

import static com.celements.common.test.CelementsTestUtils.*;
import static com.celements.payment.service.ComputopService.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.CharEncoding;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.component.manager.ComponentRepositoryException;
import org.xwiki.configuration.ConfigurationSource;

import com.celements.common.test.AbstractComponentTest;
import com.celements.payment.service.ComputopService.ReturnUrl;
import com.xpn.xwiki.web.Utils;

public class ComputopServiceTest extends AbstractComponentTest {

  private static final String DEFAULT_HMAC_TEST_KEY = "ComputopSecretHmacKey!";
  private static final String DEFAULT_HMAC_EXAMPLE_PLAIN_TEXT = "She sells sea shells by the sea shore.";
  private static final String DEFAULT_HMAC_EXAMPLE_HASHED = "5d286cc2c516e86470098ef7da266294fec3897035ba74da1ddcc60bd5732701";

  private static final SecretKey DEFAULT_BLOWFISH_KEY = new SecretKeySpec(
      "16CharKeyLength!".getBytes(), BLOWFISH);
  private static final String DEFAULT_BLOWFISH_PLAIN_TEXT = "Unencrypted plain text!";
  private static final int DEFAULT_BLOWFISH_PLAIN_TEXT_LENGTH = DEFAULT_BLOWFISH_PLAIN_TEXT.length();
  private static final String DEFAULT_BLOWFISH_MATCHING = "4105AAFDC6445BF2EB8242371136F488";
  private static final String DEFAULT_BLOWFISH_COMPUTOP_ENCODED = "4105AAFDC6445BF2EB8242371136F4886413347EEB8731B9";
  private static final String DEFAULT_BLOWFISH_INTERNET_ENCODED = "4105aafdc6445bf2eb8242371136f488a4812b38a821e753";

  private ComputopService service;
  private ConfigurationSource configSrcMock;

  @Before
  public void prepareTest() throws ComponentRepositoryException {
    configSrcMock = registerComponentMock(ConfigurationSource.class);
    service = (ComputopService) Utils.getComponent(ComputopServiceRole.class);
  }

  @Test
  public void testGetAmount() {
    assertEquals("2.00", service.getAmount(new BigDecimal(2)));
    assertEquals("3.55", service.getAmount(new BigDecimal(3.55D)));
    assertEquals("21.30", service.getAmount(new BigDecimal(21.3F)));
    assertEquals("0.51", service.getAmount(new BigDecimal(.51)));
  }

  @Test
  public void testHashPaymentData() {
    expect(configSrcMock.getProperty(eq(HMAC_SECRET_KEY_PROP), eq(""))).andReturn(
        DEFAULT_HMAC_TEST_KEY);
    replayDefault();
    assertEquals(DEFAULT_HMAC_EXAMPLE_HASHED, service.hashPaymentData(
        DEFAULT_HMAC_EXAMPLE_PLAIN_TEXT));
    verifyDefault();
  }

  @Test
  public void testGetPaymentDataHmac() {
    String paymentHmac = "1df273c64b4342265e92357f7f3fb1cdfbfbe3e3c89d2fb8d93c25411a1a2285";
    expect(configSrcMock.getProperty(eq(HMAC_SECRET_KEY_PROP), eq(""))).andReturn(
        DEFAULT_HMAC_TEST_KEY);
    replayDefault();
    assertEquals(paymentHmac, service.getPaymentDataHmac("payId", "transId", "merchantId",
        new BigDecimal(23.3D), "CHF"));
    verifyDefault();
  }

  @Test
  public void testGetPaymentDataHmac_nullField() {
    String paymentHmac = "0678abd0cbc568254ab4a4ecff7beae7a1d3398cc106e2df50f815820c489a87";
    expect(configSrcMock.getProperty(eq(HMAC_SECRET_KEY_PROP), eq(""))).andReturn(
        DEFAULT_HMAC_TEST_KEY);
    replayDefault();
    assertEquals(paymentHmac, service.getPaymentDataHmac(null, "transId", "merchantId",
        new BigDecimal(23.3D), "CHF"));
    verifyDefault();
  }

  @Test
  public void testIsCallbackHashValid_true() {
    String callbackHmac = "04b7e62d9b0fc4e024edf416317861707261351a71b5fb1f464e11ec2e5d161a";
    expect(configSrcMock.getProperty(eq(HMAC_SECRET_KEY_PROP), eq(""))).andReturn(
        DEFAULT_HMAC_TEST_KEY);
    replayDefault();
    assertTrue(service.isCallbackHashValid(callbackHmac, "payId", "transId", "merchantId", "payed",
        "123"));
    verifyDefault();
  }

  @Test
  public void testIsCallbackHashValid_false() {
    String callbackHmac = "ffffe62d9b0fc4e024edf416317861707261351a71b5fb1f464e11ec2e5d161a";
    expect(configSrcMock.getProperty(eq(HMAC_SECRET_KEY_PROP), eq(""))).andReturn(
        DEFAULT_HMAC_TEST_KEY);
    replayDefault();
    assertFalse(service.isCallbackHashValid(callbackHmac, "payId", "transId", "merchantId", "payed",
        "123"));
    verifyDefault();
  }

  @Test
  public void testBlowfishEncrypt() {
    assertTrue(service.encryptString(DEFAULT_BLOWFISH_PLAIN_TEXT.getBytes(),
        DEFAULT_BLOWFISH_KEY).startsWith(DEFAULT_BLOWFISH_MATCHING.toUpperCase()));
  }

  @Test
  public void testBlowfishDecrypt_computopExampleImplementationEncrypted() {
    assertEquals(DEFAULT_BLOWFISH_PLAIN_TEXT, new String(service.decryptString(
        DEFAULT_BLOWFISH_COMPUTOP_ENCODED, DEFAULT_BLOWFISH_PLAIN_TEXT_LENGTH,
        DEFAULT_BLOWFISH_KEY)));
  }

  @Test
  public void testBlowfishDecrypt_onlineToolEncrypted() {
    assertEquals(DEFAULT_BLOWFISH_PLAIN_TEXT, new String(service.decryptString(
        DEFAULT_BLOWFISH_INTERNET_ENCODED, DEFAULT_BLOWFISH_PLAIN_TEXT_LENGTH,
        DEFAULT_BLOWFISH_KEY)));
  }

  @Test
  public void testBlowfishEncryptDecryptCycle() {
    String encrypted = service.encryptString(DEFAULT_BLOWFISH_PLAIN_TEXT.getBytes(),
        DEFAULT_BLOWFISH_KEY);
    assertEquals(DEFAULT_BLOWFISH_PLAIN_TEXT, new String(service.decryptString(encrypted,
        DEFAULT_BLOWFISH_PLAIN_TEXT_LENGTH, DEFAULT_BLOWFISH_KEY)));
  }

  @Test
  public void testGetPaymentDataPlainString() throws Exception {
    String merchantId = "merchant";
    String transactionId = "tid";
    String orderDescription = "1.35 meter of kilogram per hour";
    BigDecimal amount = new BigDecimal(32.5);
    String currency = "EUR";
    String successUrl = "https://server/success?test=x&a=b";
    String successUrlEnc = URLEncoder.encode(successUrl, CharEncoding.UTF_8.toString());
    String failureUrl = "https://server/failure";
    String failureUrlEnc = URLEncoder.encode(failureUrl, CharEncoding.UTF_8.toString());
    String notifyUrl = "https://server/notify?callback=1";
    String notifyUrlEnc = URLEncoder.encode(notifyUrl, CharEncoding.UTF_8.toString());
    expect(configSrcMock.getProperty(eq(HMAC_SECRET_KEY_PROP), eq(""))).andReturn(
        DEFAULT_HMAC_TEST_KEY).atLeastOnce();
    expect(configSrcMock.getProperty(eq(MERCHANT_ID_PROP), eq(""))).andReturn(merchantId);
    expect(configSrcMock.getProperty(eq(ReturnUrl.SUCCESS.getValue()), eq(""))).andReturn(
        successUrl);
    expect(configSrcMock.getProperty(eq(ReturnUrl.FAILURE.getValue()), eq(""))).andReturn(
        failureUrl);
    expect(configSrcMock.getProperty(eq(ReturnUrl.CALLBACK.getValue()), eq(""))).andReturn(
        notifyUrl);
    replayDefault();
    String hmac = service.getPaymentDataHmac(null, transactionId, merchantId, amount, currency);
    assertEquals(FORM_INPUT_NAME_MERCHANT_ID + "=" + merchantId + "&" + FORM_INPUT_NAME_TRANS_ID
        + "=" + transactionId + "&" + FORM_INPUT_NAME_AMOUNT + "=" + amount.setScale(2,
            RoundingMode.HALF_UP).toPlainString() + "&" + FORM_INPUT_NAME_CURRENCY + "=" + currency
        + "&" + FORM_INPUT_NAME_DESCRIPTION + "=" + orderDescription + "&" + FORM_INPUT_NAME_HMAC
        + "=" + hmac + "&" + ReturnUrl.SUCCESS.getParamName() + "=" + successUrlEnc + "&"
        + ReturnUrl.FAILURE.getParamName() + "=" + failureUrlEnc + "&"
        + ReturnUrl.CALLBACK.getParamName() + "=" + notifyUrlEnc, service.getPaymentDataPlainString(
            transactionId, orderDescription, amount, currency));
    verifyDefault();
  }

}
