package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;

/** Long-term policy resolution built on shared financial defaults. */
final class LongTermAssetPolicyRules {
  private LongTermAssetPolicyRules() {}

  static BigDecimal bondTaxRate(BigDecimal configuredRate) {
    return configuredRate == null ? FinancialPolicyDefaults.BOND_TAX_RATE : configuredRate;
  }

  static BigDecimal rentalTaxRate(BigDecimal configuredRate) {
    return configuredRate == null ? FinancialPolicyDefaults.RENTAL_TAX_RATE : configuredRate;
  }
}
