package com.smartbox.investory.ui.retirement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ScenarioAssumptionViewTest {
  @Test
  void higherInflationIsNegativeButHigherEquityReturnIsPositive() {
    var inflation = ScenarioAssumptionView.of("Inflation", bd(".03"), bd(".045"), false);
    var equity = ScenarioAssumptionView.of("Equity return", bd(".08"), bd(".09"), true);

    assertEquals(bd("1.5"), inflation.deltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.NEGATIVE, inflation.effect());
    assertEquals(bd("1.0"), equity.deltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.POSITIVE, equity.effect());
  }

  @Test
  void unchangedRateIsNeutral() {
    var row = ScenarioAssumptionView.of("Spending growth", bd(".015"), bd(".015"), false);
    assertEquals(bd("0.0"), row.deltaPercentagePoints());
    assertEquals(ScenarioAssumptionView.DeltaEffect.NEUTRAL, row.effect());
  }

  private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
