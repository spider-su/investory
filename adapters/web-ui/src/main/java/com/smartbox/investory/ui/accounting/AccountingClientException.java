package com.smartbox.investory.ui.accounting;

/** Expected Accounting API failures surfaced to the MVC layer with the HTTP status and message. */
public class AccountingClientException extends RuntimeException {
  private final int status;

  public AccountingClientException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
