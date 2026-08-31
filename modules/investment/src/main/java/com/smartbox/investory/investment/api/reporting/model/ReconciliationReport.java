package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.util.CollectionUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Immutable public report for the system-wide current-state C0-C7 reconciliation pipeline. */
public record ReconciliationReport(
    ReconciliationOverallState overallState,
    Checkpoint firstFailingCheckpoint,
    List<Checkpoint> checkpoints,
    List<IssueGroup> issueGroups,
    List<Issue> issues,
    Instant executedAt,
    LocalDate asOfDate) {

  public ReconciliationReport {
    checkpoints = CollectionUtils.immutableListOrEmpty(checkpoints);
    issueGroups = CollectionUtils.immutableListOrEmpty(issueGroups);
    issues = CollectionUtils.immutableListOrEmpty(issues);
  }

  public record Checkpoint(
      ReconciliationCheckpoint checkpoint,
      String name,
      ReconciliationStatus originalStatus,
      ReconciliationStatus effectiveStatus,
      long issueCount,
      long failureCount,
      long reviewCount) {

    public Checkpoint {
      name = name == null && checkpoint != null ? checkpoint.displayName() : name;
      originalStatus = originalStatus == null ? ReconciliationStatus.NOT_CHECKED : originalStatus;
      effectiveStatus = effectiveStatus == null ? originalStatus : effectiveStatus;
    }
  }

  public record IssueGroup(ReconciliationCheckpoint checkpoint, List<Issue> issues) {
    public IssueGroup {
      issues = CollectionUtils.immutableListOrEmpty(issues);
    }

    public String level() {
      return checkpoint.code() + " — " + checkpoint.displayName();
    }
  }

  public record Issue(
      ReconciliationStatus originalStatus,
      ReconciliationStatus effectiveStatus,
      ReconciliationCheckpoint checkpoint,
      String location,
      String checkCode,
      String checkName,
      BigDecimal expected,
      BigDecimal actual,
      BigDecimal difference,
      String cause,
      String details,
      String suggestedAction) {

    public String level() {
      return checkpoint.code() + " — " + checkpoint.displayName();
    }
  }
}
