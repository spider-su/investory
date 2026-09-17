package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Canonical revenue-register entry projected from an accepted source document. */
public record RevenueEntry(
    LocalDate revenueDate,
    String sourceDocumentId,
    String reference,
    String client,
    String currency,
    BigDecimal sourceAmount,
    LocalDate fxRateDate,
    BigDecimal revenuePln,
    BigDecimal ryczaltRate,
    RevenueType revenueType,
    String correctionReference) {
  public enum RevenueType {
    INVOICE_REVENUE,
    CORRECTION
  }
}
