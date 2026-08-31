package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;

/** Explicit availability state for a calculated portfolio return metric. */
public record ReturnMetric(Status status, BigDecimal value, String reason) {
  public enum Status {
    AVAILABLE,
    INSUFFICIENT_DATA,
    CALCULATION_FAILED
  }

  public static ReturnMetric available(BigDecimal value) {
    return new ReturnMetric(Status.AVAILABLE, value, null);
  }

  public static ReturnMetric unavailable(Status status, String reason) {
    if (status == Status.AVAILABLE) {
      throw new IllegalArgumentException("Available metric needs a value");
    }
    return new ReturnMetric(status, null, reason);
  }
}
