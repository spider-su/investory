package com.smartbox.investory.longterm.application.service;

import java.math.BigDecimal;

/** Canonical decimal-rate validation shared by every Long-Term application entry point. */
final class LongTermAssetRateRules {
  private static final BigDecimal ONE = BigDecimal.ONE;

  private LongTermAssetRateRules() {}

  static void requireReturnRate(BigDecimal rate, String label) {
    if (rate == null || rate.signum() < 0 || rate.compareTo(ONE) > 0) {
      throw new IllegalArgumentException(label + " must be between 0 and 1");
    }
  }

  static void requireGrowthRate(BigDecimal rate) {
    if (rate == null || rate.compareTo(ONE.negate()) < 0 || rate.compareTo(ONE) > 0) {
      throw new IllegalArgumentException("Expected property growth rate must be between -1 and 1");
    }
  }
}
