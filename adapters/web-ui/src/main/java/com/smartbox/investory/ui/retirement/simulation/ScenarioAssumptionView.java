package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.ui.presentation.UiPresentation;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Presentation-only scenario comparison row. Rates are ratios; delta is percentage points. */
public record ScenarioAssumptionView(
    String name,
    BigDecimal planRate,
    BigDecimal effectiveRate,
    BigDecimal deltaPercentagePoints,
    DeltaEffect effect,
    BigDecimal observedRate,
    String observedLabel,
    String observedPeriod,
    BigDecimal observedDeltaPercentagePoints,
    DeltaEffect observedEffect,
    Availability availability) {

  public String observedRateDisplay() {
    return availability == Availability.AVAILABLE && observedRate != null
        ? UiPresentation.percentage(observedRate)
        : "—";
  }

  public enum DeltaEffect {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
  }

  public enum Availability {
    AVAILABLE,
    UNAVAILABLE,
    INSUFFICIENT_HISTORY
  }

  public static ScenarioAssumptionView of(
      String name, BigDecimal planRate, BigDecimal effectiveRate, boolean higherIsBetter) {
    return of(
        name, planRate, effectiveRate, higherIsBetter, null, null, null, Availability.UNAVAILABLE);
  }

  public static ScenarioAssumptionView of(
      String name,
      BigDecimal planRate,
      BigDecimal effectiveRate,
      boolean higherIsBetter,
      BigDecimal observedRate,
      String observedLabel,
      String observedPeriod,
      Availability availability) {
    BigDecimal delta =
        effectiveRate
            .subtract(planRate)
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP);
    DeltaEffect effect =
        delta.signum() == 0
            ? DeltaEffect.NEUTRAL
            : ((delta.signum() > 0) == higherIsBetter
                ? DeltaEffect.POSITIVE
                : DeltaEffect.NEGATIVE);
    BigDecimal observedDelta =
        observedRate == null
            ? null
            : observedRate
                .subtract(effectiveRate)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    DeltaEffect observedEffect =
        observedDelta == null || observedDelta.signum() == 0
            ? DeltaEffect.NEUTRAL
            : ((observedDelta.signum() > 0) == higherIsBetter
                ? DeltaEffect.POSITIVE
                : DeltaEffect.NEGATIVE);
    return new ScenarioAssumptionView(
        name,
        planRate,
        effectiveRate,
        delta,
        effect,
        observedRate,
        observedLabel,
        observedPeriod,
        observedDelta,
        observedEffect,
        availability == null ? Availability.UNAVAILABLE : availability);
  }
}
