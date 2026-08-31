package com.smartbox.investory.application.longterm;

import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Calculates current bond planning facts without changing maturity or interest-treatment semantics.
 */
public final class BondPlanningCalculator {
  private static final BigDecimal TAX_RATE = new BigDecimal("0.19");

  public BondPlanningSummary calculate(
      BigDecimal value,
      BigDecimal annualRate,
      LocalDate maturityDate,
      InterestTreatment treatment) {
    BigDecimal gross = value.multiply(annualRate);
    BigDecimal tax = gross.multiply(TAX_RATE);
    BigDecimal net = gross.subtract(tax);
    return new BondPlanningSummary(
        value,
        annualRate,
        gross,
        tax,
        net,
        LongTermAssetCalculator.ratio(net, value),
        maturityDate,
        treatment);
  }
}
