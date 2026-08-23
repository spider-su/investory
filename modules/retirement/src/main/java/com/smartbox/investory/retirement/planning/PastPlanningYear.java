package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;
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

  public BigDecimal variance(PlanningMetric metric) {
    PlanningMetricValue actual = values == null ? null : values.get(metric);
    PlanningMetricValue expected = expectedValues == null ? null : expectedValues.get(metric);
    return actual == null || expected == null || !actual.available() || !expected.available()
        ? null
        : actual.value().subtract(expected.value());
  }

  public PastPlanningYear(
      int year,
      PlanningYearStatus status,
      Instant closedAt,
      Map<PlanningMetric, PlanningMetricValue> values,
      Map<PlanningMetric, PlanningMetricValue> expectedValues) {
    this(year, status, closedAt, null, null, values, expectedValues);
  }
}
