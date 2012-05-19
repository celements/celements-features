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
package com.celements.payment.raw;

import java.util.Date;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

public class PayPal {

  /**
   * transaction id is required
   */
  private Integer txn_id;

  private String txn_type;

  private Date payment_date;

  private String origHeader;

  private String origMessage;

  private String payerId;

  private String receiverId;

  private String paymentStatus;

  private String pending_reason;

  private String reason_code;

  private String verify_sign;

  private String invoice;

  @Enumerated(EnumType.STRING)
  private EProcessStatus processStatus;

  public PayPal() {
  }

  public String getTxn_type() {
    return txn_type;
  }

  public void setTxn_type(String txnType) {
    txn_type = txnType;
  }

  public String getPending_reason() {
    return pending_reason;
  }

  public void setPending_reason(String pendingReason) {
    pending_reason = pendingReason;
  }

  public String getReason_code() {
    return reason_code;
  }

  public void setReason_code(String reasonCode) {
    reason_code = reasonCode;
  }

  public Integer getTxn_id() {
    return txn_id;
  }

  public void setTxn_id(Integer txnId) {
    txn_id = txnId;
  }

  public Date getPayment_date() {
    return payment_date;
  }

  public void setPayment_date(Date paymentDate) {
    payment_date = paymentDate;
  }

  public String getOrigHeader() {
    return origHeader;
  }

  public void setOrigHeader(String origHeader) {
    this.origHeader = origHeader;
  }

  public String getOrigMessage() {
    return origMessage;
  }

  public void setOrigMessage(String origMessage) {
    this.origMessage = origMessage;
  }

  public String getPayerId() {
    return payerId;
  }

  public void setPayerId(String payerId) {
    this.payerId = payerId;
  }

  public String getReceiverId() {
    return receiverId;
  }

  public void setReceiverId(String receiverId) {
    this.receiverId = receiverId;
  }

  public String getPaymentStatus() {
    return paymentStatus;
  }

  public void setPaymentStatus(String paymentStatus) {
    this.paymentStatus = paymentStatus;
  }

  public String getVerify_sign() {
    return verify_sign;
  }

  public void setVerify_sign(String verifySign) {
    verify_sign = verifySign;
  }

  public String getInvoice() {
    return invoice;
  }

  public void setInvoice(String invoice) {
    this.invoice = invoice;
  }

  public EProcessStatus getProcessStatus() {
    return processStatus;
  }

  public void setProcessStatus(EProcessStatus processStatus) {
    this.processStatus = processStatus;
  }

}
