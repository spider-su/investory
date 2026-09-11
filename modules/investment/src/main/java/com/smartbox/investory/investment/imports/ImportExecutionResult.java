package com.smartbox.investory.investment.imports;

import java.util.Set;

public record ImportExecutionResult(
    int rowsTotal, int rowsApplied, int rowsFailed, String details, Set<Long> affectedAccountIds) {

  public ImportExecutionResult(int rowsTotal, int rowsApplied, int rowsFailed, String details) {
    this(rowsTotal, rowsApplied, rowsFailed, details, Set.of());
  }

  public ImportExecutionResult {
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
