package com.smartbox.investory.investment.reconciliation;

import java.time.Instant;
import java.util.List;

public record ReconciliationCheckResult(
    ReconciliationCheckpoint checkpoint,
    ReconciliationStatus status,
    long issueCount,
    long failureCount,
    long reviewCount,
    List<ReconciliationIssue> issues,
    String evidenceSource,
    Instant executedAt) {
  public ReconciliationCheckResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }
}
