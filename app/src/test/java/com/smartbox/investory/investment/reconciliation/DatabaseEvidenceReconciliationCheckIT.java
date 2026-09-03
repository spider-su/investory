package com.smartbox.investory.investment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.testsupport.FastDatabase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("Database Evidence Reconciliation Check")
class DatabaseEvidenceReconciliationCheckIT {
  private static final long TEST_ACCOUNT_ID = 9_990_001L;
  private static final long TEST_CASH_OPERATION_ID = 9_990_001L;
  private static final String RETRY_HASH = "a".repeat(64);
  private static final String NULL_STATUS_HASH = "b".repeat(64);
  private static final ReconciliationContext CONTEXT =
      new ReconciliationContext(Instant.parse("2026-08-28T10:00:00Z"), LocalDate.of(2026, 8, 28));
  private static JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void migrateDatabase() {
    var dataSource =
        new DriverManagerDataSource(
            FastDatabase.jdbcUrl(), FastDatabase.username(), FastDatabase.password());
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void cleanTestEvidence() {
    jdbcTemplate.update(
        "DELETE FROM investory.cash_operations WHERE id = ?", TEST_CASH_OPERATION_ID);
    jdbcTemplate.update("DELETE FROM investory.accounts WHERE id = ?", TEST_ACCOUNT_ID);
    jdbcTemplate.update("DELETE FROM investory.import_history WHERE file_sha256 = ?", RETRY_HASH);
    jdbcTemplate.update(
        "DELETE FROM investory.import_history WHERE file_sha256 = ?", NULL_STATUS_HASH);
  }

  @DisplayName("all Database Checkpoint Queries Execute Against Current Schema")
  @Test
  void allDatabaseCheckpointQueriesExecuteAgainstCurrentSchema() {
    for (ReconciliationCheckpoint checkpoint :
        List.of(
            ReconciliationCheckpoint.C0,
            ReconciliationCheckpoint.C1,
            ReconciliationCheckpoint.C2,
            ReconciliationCheckpoint.C5,
            ReconciliationCheckpoint.C6)) {
      assertDoesNotThrow(
          () ->
              DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbcTemplate, checkpoint)
                  .execute(CONTEXT),
          checkpoint.name());
    }
  }

  @DisplayName("canonical Happy Investor cash flows keep economic classifications")
  @Test
  void canonicalHappyInvestorCashFlowsKeepEconomicClassifications() {
    assertThat(
            jdbcTemplate.queryForObject(
                "select sum(amount) from investory.cash_operations where id in (7001,7003,7005,7007)",
                BigDecimal.class))
        .isEqualByComparingTo("116000");
    assertThat(
            jdbcTemplate.queryForObject(
                "select sum(amount) from investory.cash_operations where id in (7002,7004,7006,7008)",
                BigDecimal.class))
        .isEqualByComparingTo("-7000");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from investory.app_v_normalized_cash_operations where operation_id between 7009 and 7016 and normalized_category not in ('DIVIDEND', 'INTEREST', 'FEE', 'WITHHOLDING_TAX', 'OTHER_TAX')",
                Integer.class))
        .isEqualTo(8);
    assertThat(
            jdbcTemplate.queryForObject(
                "select amount from investory.cash_operations where id = 7017", BigDecimal.class))
        .isEqualByComparingTo("-1");
    assertThat(
            jdbcTemplate.queryForObject(
                "select amount from investory.cash_operations where id = 7018", BigDecimal.class))
        .isEqualByComparingTo("120");
    assertThat(
            jdbcTemplate.queryForObject(
                "select amount from investory.cash_operations where id = 7019", BigDecimal.class))
        .isEqualByComparingTo("-22.8");
  }

  @DisplayName("c0 Ignores A Superseded Failed Import Attempt")
  @Test
  void c0IgnoresASupersededFailedImportAttempt() {
    long failedId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO investory.import_history(
                provider, file_name, file_sha256, started_at, finished_at, status,
                rows_total, rows_failed, rows_applied, attempt_no)
            VALUES ('XTB', 'retry.csv', ?, now() - interval '2 minutes', now() - interval '1 minute',
                    'FAILED', 1, 1, 0, 1)
            RETURNING id
            """,
            Long.class,
            RETRY_HASH);
    jdbcTemplate.update(
        """
        INSERT INTO investory.import_history(
            provider, file_name, file_sha256, started_at, finished_at, status,
            rows_total, rows_failed, rows_applied, attempt_no, reprocess_of)
        VALUES ('XTB', 'retry.csv', ?, now() - interval '1 minute', now(),
                'COMPLETED', 1, 0, 1, 2, ?)
        """,
        RETRY_HASH,
        failedId);

    ReconciliationCheckResult result =
        DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbcTemplate, ReconciliationCheckpoint.C0)
            .execute(CONTEXT);

    assertThat(result.issues())
        .noneMatch(issue -> issue.location().equals("import_history / " + failedId));
  }

  @DisplayName("c0 Treats A Null Import Status As Incomplete")
  @Test
  void c0TreatsANullImportStatusAsIncomplete() {
    long importId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO investory.import_history(
                provider, file_name, file_sha256, started_at, finished_at, status,
                rows_total, rows_failed, rows_applied, attempt_no)
            VALUES ('XTB', 'null-status.csv', ?, now() - interval '1 minute', now(),
                    NULL, 1, 0, 0, 1)
            RETURNING id
            """,
            Long.class,
            NULL_STATUS_HASH);

    ReconciliationCheckResult result =
        DatabaseEvidenceReconciliationCheck.forCheckpoint(jdbcTemplate, ReconciliationCheckpoint.C0)
            .execute(CONTEXT);

    assertThat(result.issues())
        .anyMatch(issue -> issue.location().equals("import_history / " + importId));
  }

  @DisplayName("c1 Uses Full Precision Across The Display Rounding Boundary")
  @Test
  void c1UsesFullPrecisionAcrossTheDisplayRoundingBoundary() {
    insertTestAccount();
    jdbcTemplate.update(
        """
        INSERT INTO investory.cash_operations(id, account_id, operation, amount, currency, comment, date)
        SELECT ?, ?, 'DEPOSIT', 100.00000000, currency, 'cash deposit', '2026-08-20T10:00:00Z'
        FROM investory.accounts WHERE id = ?
        """,
        TEST_CASH_OPERATION_ID,
        TEST_ACCOUNT_ID,
        TEST_ACCOUNT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO investory.account_daily(
            account_id, snapshot_date, valuation_currency, cash_balance, deposits)
        SELECT a.id, snapshots.snapshot_date, a.currency, snapshots.cash_balance, snapshots.deposits
        FROM investory.accounts a
        CROSS JOIN (VALUES
            (DATE '2026-08-19', 0.00000000, 0.00000000),
            (DATE '2026-08-20', 100.06000000, 100.06000000)
        ) AS snapshots(snapshot_date, cash_balance, deposits)
        WHERE a.id = ?
        """,
        TEST_ACCOUNT_ID);
    jdbcTemplate.execute("REFRESH MATERIALIZED VIEW investory.app_v_portfolio_daily_fx_rate_mv");
    jdbcTemplate.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");

    ReconciliationCheckResult outsideTolerance = c1();
    assertThat(outsideTolerance.issues())
        .anyMatch(issue -> issue.location().startsWith("000-C1-BOUNDARY / 2026-08-20"));

    jdbcTemplate.update(
        """
        UPDATE investory.account_daily
        SET cash_balance = 100.04000000, deposits = 100.04000000
        WHERE account_id = ? AND snapshot_date = '2026-08-20'
        """,
        TEST_ACCOUNT_ID);

    ReconciliationCheckResult insideTolerance = c1();
    assertThat(insideTolerance.issues())
        .noneMatch(issue -> issue.location().startsWith("000-C1-BOUNDARY / 2026-08-20"));
  }

  @DisplayName("c1 Detects Cash Delta Without A Ledger Row")
  @Test
  void c1DetectsCashDeltaWithoutALedgerRow() {
    insertTestAccount();
    jdbcTemplate.update(
        """
        INSERT INTO investory.account_daily(account_id, snapshot_date, valuation_currency, cash_balance)
        SELECT id, '2026-08-21', currency, 100
        FROM investory.accounts WHERE id = ?
        """,
        TEST_ACCOUNT_ID);

    ReconciliationCheckResult result = c1();

    assertThat(result.issues())
        .anyMatch(issue -> issue.location().startsWith("000-C1-BOUNDARY / 2026-08-21"));
  }

  @DisplayName("c1 Caps Details But Keeps The Exact Issue Count")
  @Test
  void c1CapsDetailsButKeepsTheExactIssueCount() {
    insertTestAccount();
    jdbcTemplate.update(
        """
        INSERT INTO investory.account_daily(account_id, snapshot_date, valuation_currency, cash_balance, deposits)
        SELECT a.id, day::date, a.currency, 100, 100
        FROM investory.accounts a
        CROSS JOIN generate_series(date '2020-01-01', date '2020-09-07', interval '1 day') day
        WHERE a.id = ?
        """,
        TEST_ACCOUNT_ID);

    ReconciliationCheckResult result = c1();

    assertThat(result.issues()).hasSize(250);
    assertThat(result.issueCount()).isGreaterThanOrEqualTo(251);
  }

  private static ReconciliationCheckResult c1() {
    return DatabaseEvidenceReconciliationCheck.forCheckpoint(
            jdbcTemplate, ReconciliationCheckpoint.C1)
        .execute(CONTEXT);
  }

  private static void insertTestAccount() {
    jdbcTemplate.update(
        """
        INSERT INTO investory.accounts(
            id, external_account_id, currency, provider, name, owner, portfolio_id)
        SELECT ?, 'C1-BOUNDARY-ACCOUNT', base_currency, 'XTB', '000-C1-BOUNDARY',
               'reconciliation-test', id
        FROM investory.portfolios
        ORDER BY id
        LIMIT 1
        """,
        TEST_ACCOUNT_ID);
  }
}
