package com.smartbox.investory.ryczalt.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Semantic rounding boundaries used by the Stage 2 calculators. */
public final class RoundingPolicy {
  private RoundingPolicy() {}

  public static BigDecimal roundFxAmount(BigDecimal value) {
    return round(value, 2);
  }

  public static BigDecimal roundZusContribution(BigDecimal value) {
    return round(value, 2);
  }

  public static BigDecimal roundHealthDeduction(BigDecimal value) {
    return round(value, 2);
  }

  public static BigDecimal roundDeductionAllocation(BigDecimal value) {
    return round(value, 2);
  }

  public static BigDecimal roundRyczaltTaxableBase(BigDecimal value) {
    return round(value, 0);
  }

  public static BigDecimal roundRyczaltTax(BigDecimal value) {
    return round(value, 0);
  }

  public static BigDecimal roundVatSettlementAmount(BigDecimal value) {
    return round(value, 0);
  }

  private static BigDecimal round(BigDecimal value, int scale) {
    return Objects.requireNonNull(value, "value").setScale(scale, RoundingMode.HALF_UP);
  }
}
