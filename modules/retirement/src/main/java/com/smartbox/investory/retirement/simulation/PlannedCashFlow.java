package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.Objects;

public record PlannedCashFlow(
    String id,
    String category,
    CashFlowDirection direction,
    CashFlowCadence cadence,
    BigDecimal amount,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    LocalDate eventDate,
    ProjectionSource source,
    CurrencyType currency) {
  public PlannedCashFlow(
      String id,
      String category,
      CashFlowDirection direction,
      CashFlowCadence cadence,
      BigDecimal amount,
      LocalDate effectiveDate,
      ProjectionSource source) {
    this(id, category, direction, cadence, amount, effectiveDate, null,
        cadence == CashFlowCadence.ONE_OFF ? effectiveDate : null, source, null);
  }

  public PlannedCashFlow {
    if (id == null || id.isBlank() || direction == null || cadence == null || effectiveFrom == null) {
      throw new IllegalArgumentException("Invalid planned cash flow");
    }
    category = category == null ? "" : category;
    amount = Objects.requireNonNull(amount, "amount");
    if (amount.signum() < 0) throw new IllegalArgumentException("Cash flow amount cannot be negative");
    if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
      throw new IllegalArgumentException("Cash flow end precedes start");
    if (cadence == CashFlowCadence.ONE_OFF && eventDate == null)
      eventDate = effectiveFrom;
    source = source == null ? ProjectionSource.PROJECTED : source;
  }

  /** Compatibility accessor for callers using the original contract name. */
  public LocalDate effectiveDate() { return effectiveFrom; }
}
