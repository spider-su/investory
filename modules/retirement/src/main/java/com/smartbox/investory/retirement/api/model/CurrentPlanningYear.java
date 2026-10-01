package com.smartbox.investory.retirement.api.model;

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
    Instant baselineCreatedAt,
    Map<PlanningMetric, PlanningMetricValue> actualValues,
    Map<PlanningMetric, PlanningMetricValue> expectedValues,
    BigDecimal annualizedSpending,
    BigDecimal projectedBondReturn,
    BigDecimal projectedEquityReturn,
    BigDecimal projectedEquityStart,
    BigDecimal projectedEquityContribution) {
  public CurrentPlanningYear(
      int year,
      Long baselinePlanId,
      Instant baselineCreatedAt,
      Map<PlanningMetric, PlanningMetricValue> actualValues,
      Map<PlanningMetric, PlanningMetricValue> expectedValues,
      BigDecimal annualizedSpending,
      BigDecimal projectedBondReturn,
      BigDecimal projectedEquityReturn) {
    this(
        year,
        baselinePlanId,
        baselineCreatedAt,
        actualValues,
        expectedValues,
        annualizedSpending,
        projectedBondReturn,
        projectedEquityReturn,
        null,
        null);
  }

  public CurrentPlanningYear(
      int year,
      Long baselinePlanId,
      Instant baselineCreatedAt,
      Map<PlanningMetric, PlanningMetricValue> actualValues,
      Map<PlanningMetric, PlanningMetricValue> expectedValues) {
    this(
        year,
        baselinePlanId,
        baselineCreatedAt,
        actualValues,
        expectedValues,
        null,
        null,
        null,
        null,
        null);
  }

  public CurrentPlanningYear(
      int year,
      Long baselinePlanId,
      Instant baselineCreatedAt,
      Map<PlanningMetric, PlanningMetricValue> actualValues,
      Map<PlanningMetric, PlanningMetricValue> expectedValues,
      BigDecimal annualizedSpending) {
    this(
        year,
        baselinePlanId,
        baselineCreatedAt,
        actualValues,
        expectedValues,
        annualizedSpending,
        null,
        null,
        null,
        null);
  }

  public BigDecimal variance(PlanningMetric metric) {
    PlanningMetricValue actual = actualValues.get(metric), expected = expectedValues.get(metric);
    return actual == null || expected == null || !actual.available() || !expected.available()
        ? null
        : actual.value().subtract(expected.value());
  }
}
