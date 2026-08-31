package com.smartbox.investory.ui.retirement.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario Assumption View")
class ScenarioAssumptionViewTest {
  @DisplayName("higher Inflation Is Negative But Higher Equity Return Is Positive")
  @Test
  void higherInflationIsNegativeButHigherEquityReturnIsPositive() {
    var inflation = ScenarioAssumptionView.of("Inflation", bd(".03"), bd(".045"), false);
    var equity = ScenarioAssumptionView.of("Equity return", bd(".08"), bd(".09"), true);

    assertEquals(bd("1.5"), inflation.deltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.NEGATIVE, inflation.effect());
    assertEquals(bd("1.0"), equity.deltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.POSITIVE, equity.effect());
  }

  @DisplayName("unchanged Rate Is Neutral")
  @Test
  void unchangedRateIsNeutral() {
    var row = ScenarioAssumptionView.of("Spending growth", bd(".015"), bd(".015"), false);
    assertEquals(bd("0.0"), row.deltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.NEUTRAL, row.effect());
  }

  @DisplayName("observed Delta Is Compared With Effective Rate And Uses Impact Semantics")
  @Test
  void observedDeltaIsComparedWithEffectiveRateAndUsesImpactSemantics() {
    var row =
        ScenarioAssumptionView.of(
            "Inflation",
            bd(".03"),
            bd(".03"),
            false,
            bd(".027"),
            "Observed",
            "trailing 12 months",
            ScenarioAssumptionView.Availability.AVAILABLE);

    assertEquals(bd("-0.3"), row.observedDeltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.POSITIVE, row.observedEffect());
  }

  @DisplayName("missing Observed Data Does Not Change Scenario Delta")
  @Test
  void missingObservedDataDoesNotChangeScenarioDelta() {
    var row = ScenarioAssumptionView.of("Equity return", bd(".08"), bd(".06"), true);

    assertEquals(bd("-2.0"), row.deltaPercentagePoints());
    assertEquals(null, row.observedRate());
    assertEquals(ScenarioAssumptionView.Availability.UNAVAILABLE, row.availability());
    assertEquals("—", row.observedRateDisplay());
  }

  @DisplayName("observed Display Preserves Zero Positive And Negative Values")
  @Test
  void observedDisplayPreservesZeroPositiveAndNegativeValues() {
    var zero = observed(BigDecimal.ZERO);
    var positive = observed(bd(".051"));
    var negative = observed(bd("-.023"));

    assertEquals("0.0%", zero.observedRateDisplay());
    assertEquals("5.1%", positive.observedRateDisplay());
    assertEquals("-2.3%", negative.observedRateDisplay());
  }

  private static ScenarioAssumptionView observed(BigDecimal value) {
    return ScenarioAssumptionView.of(
        "Equity return",
        bd(".06"),
        bd(".06"),
        true,
        value,
        "Observed annualized",
        "trailing 12 months",
        ScenarioAssumptionView.Availability.AVAILABLE);
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
