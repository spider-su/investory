package com.smartbox.investory.ui.retirement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Presentation-only scenario comparison row. Rates are ratios; delta is percentage points. */
public record ScenarioAssumptionView(
    String name, BigDecimal planRate, BigDecimal effectiveRate, BigDecimal deltaPercentagePoints,
    DeltaEffect effect) {
  public enum DeltaEffect { POSITIVE, NEGATIVE, NEUTRAL }

  public static ScenarioAssumptionView of(
      String name, BigDecimal planRate, BigDecimal effectiveRate, boolean higherIsBetter) {
    BigDecimal delta = effectiveRate.subtract(planRate).multiply(BigDecimal.valueOf(100))
        .setScale(1, RoundingMode.HALF_UP);
    DeltaEffect effect = delta.signum() == 0 ? DeltaEffect.NEUTRAL
        : ((delta.signum() > 0) == higherIsBetter ? DeltaEffect.POSITIVE : DeltaEffect.NEGATIVE);
    return new ScenarioAssumptionView(name, planRate, effectiveRate, delta, effect);
  }
}
