package com.smartbox.investory.investment.reconciliation;

import java.time.Instant;
import java.time.LocalDate;

public record ReconciliationContext(Instant startedAt, LocalDate asOfDate, Long portfolioId) {
  public ReconciliationContext(Instant startedAt, LocalDate asOfDate) {
    this(startedAt, asOfDate, null);
  }
}
