package com.smartbox.investory.ryczalt.parity;

import java.math.BigDecimal;

public record ParityDifference(
    String period,
    String field,
    BigDecimal oldValue,
    BigDecimal newValue,
    Classification classification,
    String explanation) {
  public enum Classification {
    MIGRATION_MAPPING_BUG,
    NEW_CALCULATOR_BUG,
    OLD_ACCOUNTING_BUG,
    ROUNDING_DIFFERENCE,
    MISSING_LEGACY_DATA,
    INTENTIONAL_NEW_BEHAVIOR,
    INSUFFICIENT_INFORMATION
  }
}
