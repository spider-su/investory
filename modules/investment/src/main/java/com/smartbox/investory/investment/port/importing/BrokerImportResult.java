package com.smartbox.investory.investment.port.importing;

import java.util.Set;

/** Provider-neutral result returned across the broker-import port boundary. */
public record BrokerImportResult(
    int rowsTotal, int rowsApplied, int rowsFailed, String details, Set<Long> affectedAccountIds) {

  public BrokerImportResult(int rowsTotal, int rowsApplied, int rowsFailed, String details) {
    this(rowsTotal, rowsApplied, rowsFailed, details, Set.of());
  }

  public BrokerImportResult {
    affectedAccountIds = Set.copyOf(affectedAccountIds == null ? Set.of() : affectedAccountIds);
    if (rowsTotal < 0 || rowsApplied < 0 || rowsFailed < 0) {
      throw new IllegalArgumentException("Import row counters cannot be negative");
    }
    if (rowsApplied > rowsTotal || rowsFailed > rowsTotal) {
      throw new IllegalArgumentException("Import row counters exceed rowsTotal");
    }
    if ((long) rowsApplied + rowsFailed > rowsTotal) {
      throw new IllegalArgumentException("Applied and failed rows exceed rowsTotal");
    }
  }
}
