package com.smartbox.investory.ryczalt.checker;

public record PaymentIssue(Code code, String context) {
  public enum Code {
    WRONG_CURRENCY,
    UNRELATED_TRANSACTION,
    AMBIGUOUS_CANDIDATES,
    PAYMENT_AFTER_DUE_DATE,
    OVER_ALLOCATED
  }
}
