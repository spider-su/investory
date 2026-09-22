package com.smartbox.investory.ryczalt.calculation.zus;

import java.math.BigDecimal;

/** Year-specific constants; formula and rounding remain in {@link ZusCalculator}. */
public record ZusRuleSet(
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

  public BigDecimal health(BigDecimal ytdRevenue) {
    if (ytdRevenue.compareTo(thresholdLow) <= 0) return healthLow;
    if (ytdRevenue.compareTo(thresholdMedium) <= 0) return healthMedium;
    return healthHigh;
  }
}
