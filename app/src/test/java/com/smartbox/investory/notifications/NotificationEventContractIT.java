package com.smartbox.investory.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.investment.notifications.SystemAuditNotificationProducer;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.testsupport.FastDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@DisplayName("Notification Event Contract")
class NotificationEventContractIT {
  @DisplayName("fingerprint Is Unique And Schema Constraints Reject Invalid State")
  @Test
  void fingerprintIsUniqueAndSchemaConstraintsRejectInvalidState() throws SQLException {
    String fingerprint = "contract:" + UUID.randomUUID();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      insert(statement, fingerprint);
      assertThrows(SQLException.class, () -> insert(statement, fingerprint));
      connection.rollback();
    }
  }

  @DisplayName("rolled Back Candidate Is Not Durable")
  @Test
  void rolledBackCandidateIsNotDurable() throws SQLException {
    String fingerprint = "rollback:" + UUID.randomUUID();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      insert(statement, fingerprint);
      connection.rollback();
    }
    try (Connection connection = connection();
        var statement =
            connection.prepareStatement(
                "SELECT count(*) FROM investory.notification_event WHERE fingerprint = ?")) {
      statement.setString(1, fingerprint);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next());
        assertEquals(0, result.getLong(1));
      }
    }
  }

  @DisplayName("ready Error Audit Producer Publishes Required Context")
  @Test
  void readyErrorAuditProducerPublishesRequiredContext() throws SQLException {
    UUID auditId;
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      long importId;
      try (ResultSet result =
          statement.executeQuery(
              "INSERT INTO investory.import_history(provider, file_name, file_sha256, started_at, finished_at, status, rows_total, rows_failed) "
                  + "VALUES ('IBKR', 'bad.csv', repeat('a', 64), now(), now(), 'FAILED', 2, 2) RETURNING id")) {
        assertTrue(result.next());
        importId = result.getLong(1);
      }
      try (ResultSet result =
          statement.executeQuery("SELECT investory.run_system_audit(" + importId + ", 'MANUAL')")) {
        assertTrue(result.next());
        auditId = result.getObject(1, UUID.class);
      }
      connection.commit();
    }

    AtomicReference<NotificationCandidate> captured = new AtomicReference<>();
    SystemAuditNotificationProducer producer =
        new SystemAuditNotificationProducer(
            jdbc(),
            candidate -> captured.compareAndSet(null, candidate),
            Mockito.mock(ApplicationEventPublisher.class));

    assertTrue(producer.publish(auditId));
    assertEquals("SYSTEM_AUDIT_ERROR:" + auditId, captured.get().fingerprint());
    assertEquals("MANUAL", captured.get().payload().get("triggerSource"));
    assertTrue(captured.get().payload().get("checkCodes").contains("IMPORT_FAILED"));
  }

  private static void insert(Statement statement, String fingerprint) throws SQLException {
    statement.executeUpdate(
        "INSERT INTO investory.notification_event(event_type, severity, source_entity_type, source_entity_id, fingerprint, title, payload, created_at, next_attempt_at) VALUES ("
            + "'IMPORT_FAILED_OR_PARTIAL', 'ERROR', 'TEST', '1', '"
            + fingerprint
            + "', 'test', '{}'::jsonb, now(), now())");
  }

  private static JdbcTemplate jdbc() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            FastDatabase.jdbcUrl(), FastDatabase.username(), FastDatabase.password());
    return new JdbcTemplate(dataSource);
  }

  private static Connection connection() throws SQLException {
    Connection connection =
        DriverManager.getConnection(
            FastDatabase.jdbcUrl(), FastDatabase.username(), FastDatabase.password());
    connection.setAutoCommit(false);
    return connection;
  }
}
