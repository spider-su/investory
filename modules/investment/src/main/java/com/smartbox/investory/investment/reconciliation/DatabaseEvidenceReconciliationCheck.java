package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Connects checkpoints to bounded detail plus uncapped aggregate database evidence. */
final class DatabaseEvidenceReconciliationCheck {
  private static final int DETAIL_LIMIT = 250;

  private DatabaseEvidenceReconciliationCheck() {}

  static ReconciliationCheck forCheckpoint(
      JdbcTemplate jdbcTemplate, ReconciliationCheckpoint checkpoint) {
    return new QueryCheck(jdbcTemplate, checkpoint);
  }

  private record QueryCheck(JdbcTemplate jdbcTemplate, ReconciliationCheckpoint checkpoint)
      implements ReconciliationCheck {
    @Override
    public ReconciliationCheckResult execute(ReconciliationContext context) {
      List<EvidenceRow> rows =
          jdbcTemplate.query(query(checkpoint), QueryCheck::mapRow, DETAIL_LIMIT);
      long issueCount = rows.isEmpty() ? 0 : rows.getFirst().issueCount();
      long failureCount = rows.isEmpty() ? 0 : rows.getFirst().failureCount();
      long reviewCount = rows.isEmpty() ? 0 : rows.getFirst().reviewCount();
      ReconciliationStatus status = status(checkpoint, failureCount, reviewCount);
      List<ReconciliationIssue> issues =
          rows.stream()
              .map(
                  row ->
                      new ReconciliationIssue(
                          row.status(),
                          checkpoint,
                          row.location(),
                          row.checkCode(),
                          checkpoint.displayName(),
                          row.expected(),
                          row.actual(),
                          row.difference(),
                          row.cause(),
                          row.details(),
                          row.suggestedAction()))
              .toList();
      return new ReconciliationCheckResult(
          checkpoint,
          status,
          issueCount,
          failureCount,
          reviewCount,
          issues,
          evidenceSource(checkpoint),
          context.startedAt());
    }

    private static EvidenceRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
      return new EvidenceRow(
          ReconciliationStatus.valueOf(resultSet.getString("issue_status")),
          resultSet.getString("check_code"),
          resultSet.getString("location"),
          resultSet.getBigDecimal("expected"),
          resultSet.getBigDecimal("actual"),
          resultSet.getBigDecimal("difference"),
          resultSet.getString("cause"),
          resultSet.getString("details"),
          resultSet.getString("suggested_action"),
          resultSet.getLong("issue_count"),
          resultSet.getLong("failure_count"),
          resultSet.getLong("review_count"));
    }

    private static ReconciliationStatus status(
        ReconciliationCheckpoint checkpoint, long failureCount, long reviewCount) {
      if (failureCount > 0) return ReconciliationStatus.FAIL;
      if (reviewCount > 0) return ReconciliationStatus.REVIEW;
      // Current database state cannot prove that every external archive file was supplied.
      // A clean current DB is not evidence that the complete external source corpus was imported.
      if (checkpoint == ReconciliationCheckpoint.C0) return ReconciliationStatus.NOT_CHECKED;
      return ReconciliationStatus.PASS;
    }

    private static String evidenceSource(ReconciliationCheckpoint checkpoint) {
      return switch (checkpoint) {
        case C0 -> "reporting_import_provenance_issues + latest import attempts";
        case C1 -> "account_daily + normalized_cash_operations (full precision)";
        case C2 -> "reporting_position_lot_duplicates";
        case C5 -> "v_reporting_validation_summary";
        case C6 -> "v_portfolio_service_fallback_reconciliation";
        default -> checkpoint.displayName();
      };
    }

    private static String query(ReconciliationCheckpoint checkpoint) {
      String evidence =
          switch (checkpoint) {
            case C0 -> C0_EVIDENCE;
            case C1 -> C1_EVIDENCE;
            case C2 -> C2_EVIDENCE;
            case C5 -> C5_EVIDENCE;
            case C6 -> C6_EVIDENCE;
            default -> EMPTY_EVIDENCE;
          };
      return """
      WITH evidence AS (
      %s
      ), counted AS (
          SELECT evidence.*,
                 COUNT(*) OVER () AS issue_count,
                 COUNT(*) FILTER (WHERE issue_status = 'FAIL') OVER () AS failure_count,
                 COUNT(*) FILTER (WHERE issue_status = 'REVIEW') OVER () AS review_count
          FROM evidence
      )
      SELECT * FROM counted
      ORDER BY CASE issue_status WHEN 'FAIL' THEN 0 ELSE 1 END, location, check_code
      LIMIT ?
      """
          .formatted(evidence);
    }
  }

  private static final String C0_EVIDENCE =
      """
      WITH latest_attempt AS (
          SELECT DISTINCT ON (provider, file_sha256) *
          FROM investory.import_history
          ORDER BY provider, file_sha256, attempt_no DESC, id DESC
      )
      SELECT 'FAIL'::text AS issue_status,
             issue_code AS check_code,
             financial_table || ' / ' || financial_row_id AS location,
             NULL::numeric AS expected,
             NULL::numeric AS actual,
             NULL::numeric AS difference,
             'Import provenance is inconsistent'::text AS cause,
             'importHistoryId=' || COALESCE(import_history_id::text, 'none')
               || ', sourceRowId=' || COALESCE(import_source_row_id::text, 'none') AS details,
             'Inspect import provenance and source identity.'::text AS suggested_action
      FROM investory.reporting_import_provenance_issues
      UNION ALL
      SELECT 'FAIL',
             'IMPORT_NOT_COMPLETED',
             'import_history / ' || id,
             0::numeric,
             COALESCE(rows_failed, 0)::numeric,
             COALESCE(rows_failed, 0)::numeric,
             'Import attempt is incomplete or contains failed rows',
             'status=' || status || ', finishedAt=' || COALESCE(finished_at::text, 'none'),
             'Inspect the import attempt and failed source rows.'
      FROM latest_attempt
      WHERE status IS DISTINCT FROM 'COMPLETED'
         OR finished_at IS NULL
         OR COALESCE(rows_failed, 0) > 0
      """;

  private static final String C1_EVIDENCE =
      """
      SELECT CASE
                 WHEN NOT COALESCE(r.is_complete, false) THEN 'FAIL'
                 WHEN r.account_currency = r.ledger_base_currency
                      AND NOT investory.reconciliation_values_match(r.account_cash_delta, r.ledger_cash_delta)
                   THEN 'FAIL'
                 WHEN r.internal_operation_count > 0
                           AND (NOT investory.reconciliation_values_match(r.deposits, r.ledger_deposits)
                                OR NOT investory.reconciliation_values_match(r.withdrawals, r.ledger_withdrawals))
                           AND investory.reconciliation_values_match(r.dividends, r.ledger_dividends)
                           AND investory.reconciliation_values_match(r.interest, r.ledger_interest)
                           AND investory.reconciliation_values_match(r.fees, r.ledger_fees)
                           AND investory.reconciliation_values_match(r.taxes, r.ledger_taxes)
                   THEN 'REVIEW'
             ELSE 'FAIL'
         END::text AS issue_status,
             'ACCOUNT_DAILY_CASHFLOW'::text AS check_code,
             account.name || ' / ' || r.snapshot_date AS location,
             0::numeric AS expected,
             GREATEST(COALESCE(ABS(CASE WHEN r.account_currency = r.ledger_base_currency THEN r.same_currency_cash_delta_gap END), 0),
                     COALESCE(ABS(r.deposits_gap), 0), COALESCE(ABS(r.withdrawals_gap), 0),
                     COALESCE(ABS(r.dividends_gap), 0), COALESCE(ABS(r.interest_gap), 0),
                     COALESCE(ABS(r.fees_gap), 0), COALESCE(ABS(r.taxes_gap), 0)) AS actual,
                 GREATEST(COALESCE(ABS(CASE WHEN r.account_currency = r.ledger_base_currency THEN r.same_currency_cash_delta_gap END), 0),
                     COALESCE(ABS(r.deposits_gap), 0), COALESCE(ABS(r.withdrawals_gap), 0),
                     COALESCE(ABS(r.dividends_gap), 0), COALESCE(ABS(r.interest_gap), 0),
                     COALESCE(ABS(r.fees_gap), 0), COALESCE(ABS(r.taxes_gap), 0)) AS difference,
             CASE WHEN NOT COALESCE(r.is_complete, false)
                  THEN 'Required FX evidence is incomplete'
                  WHEN r.internal_operation_count > 0
                           AND (NOT investory.reconciliation_values_match(r.deposits, r.ledger_deposits)
                                OR NOT investory.reconciliation_values_match(r.withdrawals, r.ledger_withdrawals))
                           AND investory.reconciliation_values_match(r.dividends, r.ledger_dividends)
                           AND investory.reconciliation_values_match(r.interest, r.ledger_interest)
                           AND investory.reconciliation_values_match(r.fees, r.ledger_fees)
                           AND investory.reconciliation_values_match(r.taxes, r.ledger_taxes)
                  THEN 'Cash-flow component scope includes internal transfer activity'
                  ELSE 'Cash-flow fields exceed effective reconciliation tolerance' END AS cause,
              'cash=' || COALESCE(CASE WHEN r.account_currency = r.ledger_base_currency THEN r.same_currency_cash_delta_gap::text END, 'n/a')
                    || ', deposits=' || COALESCE(r.deposits_gap::text, 'n/a')
                    || ', withdrawals=' || COALESCE(r.withdrawals_gap::text, 'n/a')
                    || ', dividends=' || COALESCE(r.dividends_gap::text, 'n/a')
                    || ', interest=' || COALESCE(r.interest_gap::text, 'n/a')
                    || ', fees=' || COALESCE(r.fees_gap::text, 'n/a')
                    || ', taxes=' || COALESCE(r.taxes_gap::text, 'n/a') AS details,
             'Inspect normalized cash operations and account_daily flow reconstruction.'::text AS suggested_action
           FROM investory.reconciliation_account_daily_cashflow_full_precision r
       JOIN investory.accounts account ON account.id = r.account_id
       WHERE NOT COALESCE(r.is_complete, false)
              OR (r.account_currency = r.ledger_base_currency
                  AND NOT investory.reconciliation_values_match(r.account_cash_delta, r.ledger_cash_delta))
              OR NOT investory.reconciliation_values_match(r.deposits, r.ledger_deposits)
              OR NOT investory.reconciliation_values_match(r.withdrawals, r.ledger_withdrawals)
              OR NOT investory.reconciliation_values_match(r.dividends, r.ledger_dividends)
              OR NOT investory.reconciliation_values_match(r.interest, r.ledger_interest)
              OR NOT investory.reconciliation_values_match(r.fees, r.ledger_fees)
              OR NOT investory.reconciliation_values_match(r.taxes, r.ledger_taxes)
      """;

  private static final String C2_EVIDENCE =
      """
      SELECT 'FAIL'::text AS issue_status,
             'DUPLICATE_POSITION_LOT'::text AS check_code,
             account.name || ' / ' || asset.symbol || ' / ' || COALESCE(r.open_time::text, 'no open time') AS location,
             1::numeric AS expected,
             r.duplicate_count::numeric AS actual,
             (r.duplicate_count - 1)::numeric AS difference,
             'Multiple position rows share the same canonical lot identity'::text AS cause,
             'positionIds=' || r.position_ids::text AS details,
             'Inspect source identity and duplicate-import handling.'::text AS suggested_action
      FROM investory.reporting_position_lot_duplicates r
      JOIN investory.accounts account ON account.id = r.account_id
      JOIN investory.assets asset ON asset.id = r.asset_id
      """;

  private static final String C5_EVIDENCE =
      """
      SELECT CASE WHEN r.status = 'FAIL' THEN 'FAIL' ELSE 'REVIEW' END::text AS issue_status,
             'REPORTING_VALIDATION_' || r.status AS check_code,
             account.name || ' / ' || r.valuation_date AS location,
             0::numeric AS expected,
             GREATEST(ABS(r.maximum_market_value_difference), ABS(r.maximum_equity_difference)) AS actual,
             GREATEST(ABS(r.maximum_market_value_difference), ABS(r.maximum_equity_difference)) AS difference,
             CASE WHEN r.status = 'FAIL' THEN 'Reporting validation contains material failures'
                  ELSE 'Reporting validation contains review-quality inputs' END AS cause,
             'missingPrices=' || r.missing_prices || ', missingFx=' || r.missing_fx_rates
               || ', reconciliationFailures=' || r.reconciliation_failures AS details,
             'Inspect reporting validation and lower-level valuation evidence.'::text AS suggested_action
      FROM investory.v_reporting_validation_summary r
      JOIN investory.accounts account ON account.id = r.account_id
      WHERE r.status IN ('FAIL', 'WARN')
      """;

  private static final String C6_EVIDENCE =
      """
      SELECT 'REVIEW'::text AS issue_status,
             'DASHBOARD_FALLBACK_REVIEW'::text AS check_code,
             'portfolio / ' || r.portfolio_id AS location,
             r.canonical_realized_profit AS expected,
             r.fallback_realized_profit AS actual,
             r.realized_profit_difference AS difference,
             'Dashboard fallback does not match canonical reporting'::text AS cause,
             'unrealizedDifference=' || COALESCE(r.unrealized_profit_difference::text, 'n/a')
               || ', dividendsDifference=' || COALESCE(r.dividends_difference::text, 'n/a')
               || ', interestDifference=' || COALESCE(r.interest_difference::text, 'n/a')
               || ', missingFx=' || r.missing_fx_count AS details,
             'Use canonical reporting and inspect fallback consumers.'::text AS suggested_action
      FROM investory.v_portfolio_service_fallback_reconciliation r
      WHERE r.fallback_reconciliation_status <> 'MATCH'
      """;

  private static final String EMPTY_EVIDENCE =
      """
      SELECT NULL::text AS issue_status, NULL::text AS check_code, NULL::text AS location,
             NULL::numeric AS expected, NULL::numeric AS actual, NULL::numeric AS difference,
             NULL::text AS cause, NULL::text AS details, NULL::text AS suggested_action
      WHERE false
      """;

  private record EvidenceRow(
      ReconciliationStatus status,
      String checkCode,
      String location,
      BigDecimal expected,
      BigDecimal actual,
      BigDecimal difference,
      String cause,
      String details,
      String suggestedAction,
      long issueCount,
      long failureCount,
      long reviewCount) {}
}
