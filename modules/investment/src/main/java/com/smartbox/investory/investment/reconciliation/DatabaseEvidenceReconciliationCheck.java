package com.smartbox.investory.investment.reconciliation;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Connects checkpoints to existing read-only reporting evidence without reproducing accounting
 * rules in Java.
 */
final class DatabaseEvidenceReconciliationCheck {
  private DatabaseEvidenceReconciliationCheck() {}

  static ReconciliationCheck forCheckpoint(
      JdbcTemplate jdbcTemplate, ReconciliationCheckpoint checkpoint) {
    return new QueryCheck(jdbcTemplate, checkpoint);
  }

  private record QueryCheck(JdbcTemplate jdbcTemplate, ReconciliationCheckpoint checkpoint)
      implements ReconciliationCheck {
    @Override
    public ReconciliationCheckResult execute(ReconciliationContext context) {
      long count = jdbcTemplate.queryForObject(query(checkpoint), Long.class);
      ReconciliationStatus status = status(checkpoint, count, context.mode());
      List<ReconciliationIssue> issues =
          count == 0
              ? List.of()
              : List.of(
                  new ReconciliationIssue(
                      status == ReconciliationStatus.REVIEW
                          ? ReconciliationStatus.REVIEW
                          : ReconciliationStatus.FAIL,
                      checkpoint,
                      checkpoint.displayName(),
                      "DATABASE_EVIDENCE",
                      checkpoint.displayName(),
                      null,
                      null,
                      null,
                      "Existing reporting evidence contains diagnostics",
                      "Uncapped evidence rows: " + count,
                      "Inspect the source reporting view."));
      return new ReconciliationCheckResult(
          checkpoint,
          status,
          count,
          status == ReconciliationStatus.FAIL ? count : 0,
          status == ReconciliationStatus.REVIEW ? count : 0,
          issues,
          evidenceSource(checkpoint),
          Instant.now());
    }

    private static ReconciliationStatus status(
        ReconciliationCheckpoint checkpoint, long count, ReconciliationMode mode) {
      if (count > 0) return ReconciliationStatus.FAIL;
      // QUICK has no external archive manifest, so persisted evidence cannot prove completeness.
      if (checkpoint == ReconciliationCheckpoint.C0) return ReconciliationStatus.NOT_CHECKED;
      return ReconciliationStatus.PASS;
    }

    private static String evidenceSource(ReconciliationCheckpoint checkpoint) {
      return switch (checkpoint) {
        case C0 -> "reporting_import_provenance_issues + import_history";
        case C1 -> "reporting_account_daily_cashflow_reconciliation";
        case C2 -> "reporting_position_lot_duplicates";
        case C5 -> "v_reporting_validation_summary";
        default -> checkpoint.displayName();
      };
    }

    private static String query(ReconciliationCheckpoint checkpoint) {
      return switch (checkpoint) {
        case C0 ->
            """
            SELECT (SELECT COUNT(*) FROM investory.reporting_import_provenance_issues)
                 + (SELECT COUNT(*) FROM investory.import_history
                    WHERE status <> 'COMPLETED' OR finished_at IS NULL OR COALESCE(rows_failed, 0) > 0)
            """;
        case C1 ->
            """
            SELECT COUNT(*) FROM investory.reporting_account_daily_cashflow_reconciliation
            WHERE NOT COALESCE(is_complete, false)
               OR COALESCE(ABS(same_currency_cash_delta_gap), 0) <> 0
               OR COALESCE(ABS(deposits_gap), 0) <> 0
               OR COALESCE(ABS(withdrawals_gap), 0) <> 0
               OR COALESCE(ABS(dividends_gap), 0) <> 0
               OR COALESCE(ABS(interest_gap), 0) <> 0
               OR COALESCE(ABS(fees_gap), 0) <> 0
               OR COALESCE(ABS(taxes_gap), 0) <> 0
            """;
        case C2 -> "SELECT COUNT(*) FROM investory.reporting_position_lot_duplicates";
        case C5 ->
            "SELECT COUNT(*) FROM investory.v_reporting_validation_summary WHERE status IN ('FAIL', 'WARN')";
        default -> "SELECT 0";
      };
    }
  }
}
