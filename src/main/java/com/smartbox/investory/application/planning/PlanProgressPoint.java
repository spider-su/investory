package com.smartbox.investory.application.planning;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlanProgressPoint(
    int year,
    LocalDate boundary,
    BigDecimal actualNetWorth,
    BigDecimal plannedNetWorth,
    BigDecimal difference,
    PlanProgressState state,
    Long baselinePlanId,
    Long baselineRevisionId) {
  public boolean available() {
    return state != PlanProgressState.UNAVAILABLE;
  }

  /** Alias for consumers that describe the comparison outcome as a status. */
  public PlanProgressState status() {
    return state;
  }

  public LocalDate boundaryDate() {
    return boundary;
  }
}
