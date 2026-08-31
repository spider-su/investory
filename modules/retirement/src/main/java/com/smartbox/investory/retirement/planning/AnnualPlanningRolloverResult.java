package com.smartbox.investory.retirement.planning;

import java.util.List;

/** Outcome of bringing planning-year reporting state up to the calendar boundary. */
public record AnnualPlanningRolloverResult(
    int currentYear,
    boolean currentYearCreated,
    List<Integer> closedYears,
    List<Integer> pendingHistoricalYears) {
  public AnnualPlanningRolloverResult {
    closedYears = List.copyOf(closedYears == null ? List.of() : closedYears);
    pendingHistoricalYears =
        List.copyOf(pendingHistoricalYears == null ? List.of() : pendingHistoricalYears);
  }
}
