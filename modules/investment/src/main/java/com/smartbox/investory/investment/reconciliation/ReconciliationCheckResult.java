package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
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
    issues = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(issues);
  }
}
