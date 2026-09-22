package com.smartbox.investory.accounting;

import java.math.BigDecimal;

/** Database-owned annual values used by the legacy accounting reconstruction. */
public record ZusAnnualRuleSet(
    int year,
    String version,
    BigDecimal socialInsurance,
    BigDecimal labourFund,
    BigDecimal voluntarySickness,
    BigDecimal healthLow,
    BigDecimal healthMedium,
    BigDecimal healthHigh,
    BigDecimal thresholdLow,
    BigDecimal thresholdMedium) {
  public BigDecimal fullJdgSocial() {
    return socialInsurance.add(labourFund);
  }

  public BigDecimal health(ZusRules2026.HealthBand band) {
    return switch (band) {
      case LOW -> healthLow;
      case MEDIUM -> healthMedium;
      case HIGH -> healthHigh;
    };
  }

  public ZusRules2026.HealthBand healthBand(BigDecimal revenue) {
    if (revenue.compareTo(thresholdLow) <= 0) return ZusRules2026.HealthBand.LOW;
    if (revenue.compareTo(thresholdMedium) <= 0) return ZusRules2026.HealthBand.MEDIUM;
    return ZusRules2026.HealthBand.HIGH;
  }
}
