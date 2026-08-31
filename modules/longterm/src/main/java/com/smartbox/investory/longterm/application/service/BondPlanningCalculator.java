package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.InterestTreatment;
import com.smartbox.investory.longterm.application.model.BondPlanningSummary;
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
    return calculate(value, annualRate, maturityDate, treatment, TAX_RATE);
  }

  public BondPlanningSummary calculate(
      BigDecimal value,
      BigDecimal annualRate,
      LocalDate maturityDate,
      InterestTreatment treatment,
      BigDecimal taxRate) {
    BigDecimal gross = value.multiply(annualRate);
    BigDecimal tax = gross.multiply(taxRate == null ? TAX_RATE : taxRate);
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
