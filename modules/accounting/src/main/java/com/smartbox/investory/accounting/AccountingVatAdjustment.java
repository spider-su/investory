package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Traceable VAT adjustment fact supplied by an external accounting source. */
public record AccountingVatAdjustment(
    LocalDate taxPeriod,
    String adjustmentType,
    BigDecimal amount,
    String sourceSystem,
    String sourceReference,
    String affects) {
  public AccountingVatAdjustment {
    if (taxPeriod == null || adjustmentType == null || adjustmentType.isBlank())
      throw new IllegalArgumentException("VAT adjustment period and type are required");
    if (amount == null) throw new IllegalArgumentException("VAT adjustment amount is required");
    if (sourceSystem == null || sourceSystem.isBlank())
      throw new IllegalArgumentException("VAT adjustment source system is required");
    if (sourceReference == null || sourceReference.isBlank())
      throw new IllegalArgumentException("VAT adjustment source reference is required");
    if (affects == null || affects.isBlank())
      throw new IllegalArgumentException("VAT adjustment target is required");
  }
}
