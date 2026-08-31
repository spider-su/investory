package com.smartbox.investory.investment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.testsupport.SharedPostgres;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
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
  private static final WorkerDatabase DATABASE = SharedPostgres.database("reconciliation-evidence");
  private static final ReconciliationContext CONTEXT =
      new ReconciliationContext(Instant.parse("2026-08-28T10:00:00Z"), LocalDate.of(2026, 8, 28));
  private static JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void migrateDatabase() {
    Flyway.configure()
        .dataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password())
        .schemas("investory")
        .defaultSchema("investory")
        .createSchemas(true)
        .locations("classpath:sql/migration")
        .load()
        .migrate();
    var dataSource =
        new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
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

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
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
        SELECT ?, ?, 'TRANSFER', 100.00000000, currency, 'cash transfer', '2026-08-20T10:00:00Z'
        FROM investory.accounts WHERE id = ?
        """,
        TEST_CASH_OPERATION_ID,
        TEST_ACCOUNT_ID,
        TEST_ACCOUNT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO investory.account_daily(
            account_id, snapshot_date, valuation_currency, cash_balance, deposits)
        SELECT id, '2026-08-20', currency, 100.06000000, 100.06000000
        FROM investory.accounts WHERE id = ?
        """,
        TEST_ACCOUNT_ID);

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
