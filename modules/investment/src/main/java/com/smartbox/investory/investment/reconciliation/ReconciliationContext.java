package com.smartbox.investory.investment.reconciliation;

import java.time.Instant;
import java.time.LocalDate;

public record ReconciliationContext(Instant startedAt, LocalDate asOfDate) {}
