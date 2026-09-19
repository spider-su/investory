package com.smartbox.investory.ryczalt.calculation.ryczalt;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/** Already-normalized revenue and contribution facts for one calculation period. */
public record RyczaltCalculationInput(
    Map<BigDecimal, BigDecimal> revenueByRate,
    BigDecimal socialContributionDeduction,
    BigDecimal healthContributionPaid,
    BigDecimal deductionsAlreadyConsumed) {
  public RyczaltCalculationInput {
    revenueByRate = Map.copyOf(Objects.requireNonNull(revenueByRate, "revenueByRate"));
    socialContributionDeduction =
        nonNegative(socialContributionDeduction, "socialContributionDeduction");
    healthContributionPaid = nonNegative(healthContributionPaid, "healthContributionPaid");
    deductionsAlreadyConsumed = nonNegative(deductionsAlreadyConsumed, "deductionsAlreadyConsumed");
    revenueByRate.forEach(
        (rate, amount) -> {
          if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Ryczalt rate must be between 0 and 1");
          }
          if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Revenue must not be negative");
          }
        });
  }

  public RyczaltCalculationInput(
      Map<BigDecimal, BigDecimal> revenueByRate,
      BigDecimal socialContributionDeduction,
      BigDecimal healthContributionPaid) {
    this(revenueByRate, socialContributionDeduction, healthContributionPaid, BigDecimal.ZERO);
  }

  private static BigDecimal nonNegative(BigDecimal value, String name) {
    Objects.requireNonNull(value, name);
    if (value.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
    return value;
  }
}
