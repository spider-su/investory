package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;

public record PlanningMetricValue(
    PlanningMetric metric,
    BigDecimal derivedValue,
    BigDecimal approvedValue,
    PlanningValueSource source,
    String note) {
  public BigDecimal value() {
    return approvedValue == null ? derivedValue : approvedValue;
  }

  public boolean available() {
    return value() != null;
  }
}
