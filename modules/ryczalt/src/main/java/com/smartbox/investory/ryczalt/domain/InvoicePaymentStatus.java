package com.smartbox.investory.ryczalt.domain;

public enum InvoicePaymentStatus {
  MATCHED,
  PARTIALLY_MATCHED,
  UNMATCHED,
  NOT_REQUIRED;

  public static InvoicePaymentStatus fromPersisted(String value) {
    if (value == null || value.isBlank()) return UNMATCHED;
    try {
      return valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return UNMATCHED;
    }
  }
}
