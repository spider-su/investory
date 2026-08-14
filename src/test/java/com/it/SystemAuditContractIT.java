package com.it;

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
import org.junit.jupiter.api.Test;

class SystemAuditContractIT {

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
                  + "FROM investory.reporting_system_audit "
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
                  + "FROM investory.reporting_system_audit "
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
                  + "FROM investory.reporting_system_audit "
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

  @Test
  void notificationReadyMatchesPersistedNotificationStatus() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT count(*) FROM investory.reporting_system_audit "
                    + "WHERE notification_ready <> (notification_status <> 'NONE') "
                    + "OR structured_log_payload IS NULL")) {
      assertTrue(result.next());
      assertEquals(0, result.getLong(1));
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

  private static void runAudit(Statement statement, long importId) throws SQLException {
    try (ResultSet result =
        statement.executeQuery("SELECT investory.run_system_audit(" + importId + ", 'MANUAL')")) {
      assertTrue(result.next());
    }
  }

  private static Connection connection() throws SQLException {
    Connection connection =
        DriverManager.getConnection(
            FastDatabase.container().getJdbcUrl(),
            FastDatabase.container().getUsername(),
            FastDatabase.container().getPassword());
    connection.setAutoCommit(false);
    assertFalse(connection.isClosed());
    return connection;
  }
}
