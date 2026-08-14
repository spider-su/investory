package com.smartbox.investory.application.simulation;

import java.math.BigDecimal;

/** Deterministic recurring-spending boundary for the configured simulation horizon. */
public record SustainableSpendingAnalysis(
    BigDecimal currentRecurringSpending, ScenarioResult base, ScenarioResult conservative) {

  public record ScenarioResult(
      BigDecimal sustainableSpending,
      BigDecimal headroom,
      BigDecimal headroomPercentage,
      boolean currentSpendingAboveLimit,
      SustainableSpendingResultState state) {
    public boolean sustainableBoundaryFound() {
      return state == SustainableSpendingResultState.BOUNDARY_FOUND;
    }
  }
}
