package com.smartbox.investory.application.planning;

import java.time.Instant;
import java.util.Map;

public record PastPlanningYear(
    int year,
    PlanningYearStatus status,
    Instant closedAt,
    Long baselinePlanId,
    Long baselineRevisionId,
    Map<PlanningMetric, PlanningMetricValue> values,
    Map<PlanningMetric, PlanningMetricValue> expectedValues) {
  public PastPlanningYear(
      int year,
      PlanningYearStatus status,
      Instant closedAt,
      Map<PlanningMetric, PlanningMetricValue> values,
      Map<PlanningMetric, PlanningMetricValue> expectedValues) {
    this(year, status, closedAt, null, null, values, expectedValues);
  }
}
