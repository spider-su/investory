package com.smartbox.investory.accounting;

/** Normalized human reference used as the document identity within an accounting profile. */
public record AccountingDocumentReference(String value) {
  public AccountingDocumentReference {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Document reference is required");
    }
    value = value.trim();
  }

  public static AccountingDocumentReference of(String value) {
    return new AccountingDocumentReference(value);
  }
}
