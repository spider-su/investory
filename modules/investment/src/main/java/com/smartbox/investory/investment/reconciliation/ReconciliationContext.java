package com.smartbox.investory.investment.reconciliation;

import java.time.Instant;
import java.time.LocalDate;

public record ReconciliationContext(
    ReconciliationMode mode, Instant startedAt, LocalDate asOfDate) {}
