package com.smartbox.investory.services.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Presentation model for the ordered C0-C7 reconciliation pipeline. */
public record ReconciliationReport(
    String overallState,
    Checkpoint firstFailingCheckpoint,
    List<Checkpoint> checkpoints,
    List<IssueGroup> issueGroups,
    List<Issue> issues,
    ReconciliationMode mode,
    Instant executedAt,
    java.time.LocalDate asOfDate) {

  public ReconciliationReport(
      String overallState,
      Checkpoint firstFailingCheckpoint,
      List<Checkpoint> checkpoints,
      List<IssueGroup> issueGroups,
      List<Issue> issues) {
    this(
        overallState,
        firstFailingCheckpoint,
        checkpoints,
        issueGroups,
        issues,
        ReconciliationMode.QUICK,
        null,
        null);
  }

  public ReconciliationReport {
    checkpoints = checkpoints == null ? List.of() : List.copyOf(checkpoints);
    issueGroups = issueGroups == null ? List.of() : List.copyOf(issueGroups);
    issues = issues == null ? List.of() : List.copyOf(issues);
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

    /** Compatibility constructor for existing callers while they migrate to typed fields. */
    public Checkpoint(String code, String name, long issueCount, long failureCount) {
      this(
          ReconciliationCheckpoint.valueOf(code),
          name,
          issueCount == 0 ? ReconciliationStatus.NOT_CHECKED : ReconciliationStatus.FAIL,
          issueCount == 0 ? ReconciliationStatus.NOT_CHECKED : ReconciliationStatus.FAIL,
          issueCount,
          failureCount,
          Math.max(0, issueCount - failureCount));
    }

    public String code() {
      return checkpoint.code();
    }

    public ReconciliationStatus status() {
      return effectiveStatus;
    }
  }

  public record IssueGroup(ReconciliationCheckpoint checkpoint, List<Issue> issues) {
    public IssueGroup {
      issues = issues == null ? List.of() : List.copyOf(issues);
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

    public String status() {
      return effectiveStatus.name();
    }

    public String level() {
      return checkpoint.code() + " — " + checkpoint.displayName();
    }
  }
}
