package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record PlannedCashFlow(
    String id,
    String category,
    CashFlowDirection direction,
    CashFlowCadence cadence,
    BigDecimal amount,
    LocalDate effectiveDate,
    ProjectionSource source) {
  public PlannedCashFlow {
    if (id == null || id.isBlank() || direction == null || cadence == null || effectiveDate == null) {
      throw new IllegalArgumentException("Invalid planned cash flow");
    }
    category = category == null ? "" : category;
    amount = Objects.requireNonNull(amount, "amount");
    if (amount.signum() < 0) throw new IllegalArgumentException("Cash flow amount cannot be negative");
    source = source == null ? ProjectionSource.PROJECTED : source;
  }
}
