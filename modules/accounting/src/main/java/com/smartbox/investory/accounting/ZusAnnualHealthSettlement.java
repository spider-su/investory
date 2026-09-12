package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Deterministic year-end comparison for the supported 2026 ryczałt health bands. */
public record ZusAnnualHealthSettlement(
    BigDecimal finalAnnualRevenue,
    ZusRules2026.HealthBand finalHealthBand,
    BigDecimal contributionsPaid,
    BigDecimal requiredAnnualContribution,
    BigDecimal difference) {
  public ZusAnnualHealthSettlement {
    finalAnnualRevenue = required(finalAnnualRevenue, "final annual revenue");
    finalHealthBand =
        finalHealthBand == null ? ZusRules2026.healthBand(finalAnnualRevenue) : finalHealthBand;
    contributionsPaid = required(contributionsPaid, "paid health contributions");
    requiredAnnualContribution =
        requiredAnnualContribution == null
            ? finalHealthBand.monthlyAmount().multiply(BigDecimal.valueOf(12))
            : required(requiredAnnualContribution, "required annual contribution");
    difference = contributionsPaid.subtract(requiredAnnualContribution);
  }

  public boolean additionalPaymentRequired() {
    return difference.signum() < 0;
  }

  public boolean overpayment() {
    return difference.signum() > 0;
  }

  private static BigDecimal required(BigDecimal value, String name) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
    return value;
  }
}
