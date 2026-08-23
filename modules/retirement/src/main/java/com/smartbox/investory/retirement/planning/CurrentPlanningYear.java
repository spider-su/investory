package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Live facts are calculated on every read; only baseline and manual planning overrides are
 * persisted.
 */
public record CurrentPlanningYear(
    int year,
    Long baselinePlanId,
    Long baselineRevisionId,
    Instant baselineCreatedAt,
    Map<PlanningMetric, PlanningMetricValue> actualValues,
    Map<PlanningMetric, PlanningMetricValue> expectedValues) {
  public CurrentPlanningYear(
      int year,
      Long baselinePlanId,
      Instant baselineCreatedAt,
      Map<PlanningMetric, PlanningMetricValue> actualValues,
      Map<PlanningMetric, PlanningMetricValue> expectedValues) {
    this(year, baselinePlanId, null, baselineCreatedAt, actualValues, expectedValues);
  }

  public BigDecimal variance(PlanningMetric metric) {
    PlanningMetricValue actual = actualValues.get(metric), expected = expectedValues.get(metric);
    return actual == null || expected == null || !actual.available() || !expected.available()
        ? null
        : actual.value().subtract(expected.value());
  }
}
