package com.smartbox.investory.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.FastDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("System Audit Contract")
class SystemAuditContractIT {

  @DisplayName("finalized Import Commits Without Synchronously Running Audit")
  @Test
  void finalizedImportCommitsWithoutSynchronouslyRunningAudit() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      long importId;
      try (ResultSet result =
          statement.executeQuery(
              "INSERT INTO investory.import_history("
                  + "provider, file_name, file_sha256, started_at, status) VALUES ("
                  + "'XTB', 'audit-completed.csv', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', now(), 'STARTED') "
                  + "RETURNING id")) {
        assertTrue(result.next());
        importId = result.getLong(1);
      }

      assertEquals(0, auditCount(statement, importId));

      statement.execute(
          "UPDATE investory.import_history SET status = 'COMPLETED', finished_at = now(), "
              + "rows_total = 10, rows_applied = 10, rows_failed = 0 WHERE id = "
              + importId);

      assertEquals(0, auditCount(statement, importId));
      connection.commit();

      runAudit(statement, importId);
      assertEquals(1, auditCount(statement, importId));
      try (ResultSet result =
          statement.executeQuery(
              "SELECT audit_status, finished_at, structured_log_payload "
                  + "FROM investory.recon_v_system_audit "
                  + "WHERE import_history_id = "
                  + importId)) {
        assertTrue(result.next());
        assertNotNull(result.getString("audit_status"));
        assertNotNull(result.getObject("finished_at"));
        String payload = result.getString("structured_log_payload");
        assertTrue(payload.contains("audit_run_id"));
        assertTrue(payload.contains("notification_status"));
        assertTrue(payload.contains("issues"));
      }
    }
  }

  @DisplayName("failed Import Produces Notification Ready Error")
  @Test
  void failedImportProducesNotificationReadyError() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      long importId;
      try (ResultSet result =
          statement.executeQuery(
              "INSERT INTO investory.import_history("
                  + "provider, file_name, file_sha256, started_at, finished_at, status, "
                  + "rows_total, rows_failed, error_message) VALUES ("
                  + "'IBKR', 'audit-failed.csv', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', now(), now(), "
                  + "'FAILED', 5, 5, 'invalid source row') RETURNING id")) {
        assertTrue(result.next());
        importId = result.getLong(1);
      }

      runAudit(statement, importId);

      try (ResultSet result =
          statement.executeQuery(
              "SELECT audit_status, notification_status, notification_ready, "
                  + "structured_log_payload "
                  + "FROM investory.recon_v_system_audit "
                  + "WHERE import_history_id = "
                  + importId)) {
        assertTrue(result.next());
        assertEquals("ERROR", result.getString("audit_status"));
        assertEquals("READY_ERROR", result.getString("notification_status"));
        assertTrue(result.getBoolean("notification_ready"));
        assertTrue(result.getString("structured_log_payload").contains("IMPORT_FAILED"));
      }
    }
  }

  @DisplayName("audit Runs Are Retained And Finished Timestamp Changes Do Not Rerun Audit")
  @Test
  void auditRunsAreRetainedAndFinishedTimestampChangesDoNotRerunAudit() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      long importId;
      try (ResultSet result =
          statement.executeQuery(
              "INSERT INTO investory.import_history("
                  + "provider, file_name, file_sha256, started_at, finished_at, status, "
                  + "rows_total, rows_applied, rows_failed) VALUES ("
                  + "'XTB', 'audit-history.csv', 'eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', now(), now(), "
                  + "'COMPLETED', 1, 1, 0) RETURNING id")) {
        assertTrue(result.next());
        importId = result.getLong(1);
      }

      assertEquals(0, auditCount(statement, importId));

      statement.execute(
          "UPDATE investory.import_history SET finished_at = finished_at + interval '1 second' "
              + "WHERE id = "
              + importId);
      assertEquals(0, auditCount(statement, importId));

      runAudit(statement, importId);
      assertEquals(1, auditCount(statement, importId));
    }
  }

  @DisplayName("partial Import Produces Persisted Warning When No Error Overrides It")
  @Test
  void partialImportProducesPersistedWarningWhenNoErrorOverridesIt() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      long importId;
      try (ResultSet result =
          statement.executeQuery(
              "INSERT INTO investory.import_history("
                  + "provider, file_name, file_sha256, started_at, finished_at, status, "
                  + "rows_total, rows_applied, rows_failed) VALUES ("
                  + "'XTB', 'audit-partial.csv', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', now(), now(), "
                  + "'PARTIAL', 10, 9, 1) RETURNING id")) {
        assertTrue(result.next());
        importId = result.getLong(1);
      }

      runAudit(statement, importId);

      try (ResultSet result =
          statement.executeQuery(
              "SELECT count(*) FROM investory.system_audit_issues issue "
                  + "JOIN investory.system_audit_runs run ON run.id = issue.audit_run_id "
                  + "WHERE run.import_history_id = "
                  + importId
                  + " AND issue.check_code = 'IMPORT_PARTIAL' "
                  + "AND issue.severity = 'WARN' AND issue.issue_count = 1")) {
        assertTrue(result.next());
        assertEquals(1, result.getLong(1));
      }
    }
  }

  @DisplayName("unresolved Transaction Is Persisted As Audit Error")
  @Test
  void unresolvedTransactionIsPersistedAsAuditError() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.cash_operations("
              + "id, account_id, operation, amount, currency, comment, date) "
              + "SELECT -980001, id, 'UNKNOWN', 1, currency, 'audit contract unknown', now() "
              + "FROM investory.accounts ORDER BY id LIMIT 1");

      long importId;
      try (ResultSet result =
          statement.executeQuery(
              "INSERT INTO investory.import_history("
                  + "provider, file_name, file_sha256, started_at, finished_at, status, "
                  + "rows_total, rows_applied, rows_failed) VALUES ("
                  + "'XTB', 'audit-unknown.csv', 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', now(), now(), "
                  + "'COMPLETED', 1, 1, 0) RETURNING id")) {
        assertTrue(result.next());
        importId = result.getLong(1);
      }

      runAudit(statement, importId);

      try (ResultSet result =
          statement.executeQuery(
              "SELECT audit_status, error_count, notification_ready "
                  + "FROM investory.recon_v_system_audit "
                  + "WHERE import_history_id = "
                  + importId)) {
        assertTrue(result.next());
        assertEquals("ERROR", result.getString("audit_status"));
        assertTrue(result.getLong("error_count") >= 1);
        assertTrue(result.getBoolean("notification_ready"));
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT count(*) FROM investory.system_audit_issues issue "
                  + "JOIN investory.system_audit_runs run ON run.id = issue.audit_run_id "
                  + "WHERE run.import_history_id = "
                  + importId
                  + " AND issue.check_code = 'UNSUPPORTED_TRANSACTION_STATE'")) {
        assertTrue(result.next());
        assertEquals(1, result.getLong(1));
      }
    }
  }

  @DisplayName("notification Ready Matches Persisted Notification Status")
  @Test
  void notificationReadyMatchesPersistedNotificationStatus() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM investory.recon_v_system_audit "
                    + "WHERE notification_ready <> (notification_status <> 'NONE') "
                    + "OR structured_log_payload IS NULL")) {
      assertTrue(result.next());
      assertEquals(0, result.getLong(1));
    }
  }

  @DisplayName("reprocessed Artifact Is Not Reported As Current Provenance Failure")
  @Test
  void reprocessedArtifactIsNotReportedAsCurrentProvenanceFailure() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      String checksum = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
      long originalId = insertImport(statement, checksum, 1, null);
      long retryId = insertImport(statement, checksum, 2, originalId);
      long sourceFileId =
          insertSourceFile(statement, originalId, checksum, "reprocessed-audit.csv");
      long oldSourceRowId = insertSourceRow(statement, originalId, sourceFileId, "old-row");
      long currentSourceRowId = insertSourceRow(statement, retryId, sourceFileId, "current-row");

      long accountId;
      try (ResultSet result =
          statement.executeQuery("SELECT id FROM investory.accounts ORDER BY id LIMIT 1")) {
        assertTrue(result.next());
        accountId = result.getLong(1);
      }
      statement.execute(
          "INSERT INTO investory.cash_operations"
              + "(id, account_id, operation, amount, currency, comment, date, import_history_id, import_source_row_id) VALUES ("
              + "-990001, "
              + accountId
              + ", 'DEPOSIT', 1, 'USD', 'reprocessed audit', now(), "
              + retryId
              + ", "
              + currentSourceRowId
              + ")");

      try (ResultSet result =
          statement.executeQuery(
              "SELECT count(*) FROM investory.recon_v_import_provenance_issues "
                  + "WHERE import_history_id = "
                  + retryId
                  + " AND issue_code IN ('SOURCE_ROW_WRONG_IMPORT', 'ORPHAN_SOURCE_ROW', 'DUPLICATE_SOURCE_IDENTITY')")) {
        assertTrue(result.next());
        assertEquals(0, result.getLong(1), issueDetails(statement, retryId));
      }
      assertTrue(oldSourceRowId > 0);
    }
  }

  private static long insertImport(
      Statement statement, String checksum, int attemptNo, Long reprocessOf) throws SQLException {
    String reprocessValue = reprocessOf == null ? "NULL" : reprocessOf.toString();
    try (ResultSet result =
        statement.executeQuery(
            "INSERT INTO investory.import_history(provider, file_name, file_sha256, started_at, finished_at, status, rows_total, rows_applied, rows_failed, attempt_no, reprocess_of) VALUES ("
                + "'XTB', 'reprocessed-audit.csv', '"
                + checksum
                + "', now(), now(), 'COMPLETED', 1, 1, 0, "
                + attemptNo
                + ", "
                + reprocessValue
                + ") RETURNING id")) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static long insertSourceFile(
      Statement statement, long importId, String checksum, String fileName) throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "INSERT INTO investory.import_source_files(provider, import_history_id, file_name, content_type, file_sha256, original_size, raw_payload) VALUES ("
                + "'XTB', "
                + importId
                + ", '"
                + fileName
                + "', 'text/csv', '"
                + checksum
                + "', 1, decode('00', 'hex')) RETURNING id")) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static long insertSourceRow(
      Statement statement, long importId, long sourceFileId, String sourceRecordId)
      throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "INSERT INTO investory.import_source_rows(import_history_id, source_file_id, provider, section_name, sheet_name, source_row_number, source_record_id, source_row_occurrence, raw_values) VALUES ("
                + importId
                + ", "
                + sourceFileId
                + ", 'XTB', 'Cash Operations', 'Cash Operations', 1, '"
                + sourceRecordId
                + "', 1, '{}'::jsonb) RETURNING id")) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static long auditCount(Statement statement, long importId) throws SQLException {
    try (ResultSet result =
        statement.executeQuery(
            "SELECT count(*) FROM investory.system_audit_runs "
                + "WHERE import_history_id = "
                + importId)) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static String issueDetails(Statement statement, long importId) throws SQLException {
    StringBuilder details = new StringBuilder();
    try (ResultSet result =
        statement.executeQuery(
            "SELECT issue_code, financial_row_id FROM investory.recon_v_import_provenance_issues WHERE import_history_id = "
                + importId)) {
      while (result.next()) {
        if (details.length() > 0) details.append(", ");
        details.append(result.getString(1)).append("=").append(result.getString(2));
      }
    }
    return details.toString();
  }

  private static void runAudit(Statement statement, long importId) throws SQLException {
    try (ResultSet result =
        statement.executeQuery("SELECT investory.run_system_audit(" + importId + ", 'MANUAL')")) {
      assertTrue(result.next());
    }
  }

  private static Connection connection() throws SQLException {
    Connection connection =
        DriverManager.getConnection(
            FastDatabase.jdbcUrl(), FastDatabase.username(), FastDatabase.password());
    connection.setAutoCommit(false);
    assertFalse(connection.isClosed());
    return connection;
  }
}
