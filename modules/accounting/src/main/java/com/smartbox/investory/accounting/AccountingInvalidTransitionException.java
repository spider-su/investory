package com.smartbox.investory.accounting;

/** Domain conflict raised when a period cannot move to the requested state. */
public final class AccountingInvalidTransitionException extends IllegalStateException {
  public AccountingInvalidTransitionException(String message) {
    super(message);
  }
}
