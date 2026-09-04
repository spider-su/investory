package com.smartbox.investory.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.smartbox.investory.testsupport.FastDatabase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class NotificationEventClaimContractIT {
  @Test
  void concurrentWorkersCannotClaimTheSameEvent() throws Exception {
    long id = insertEvent();
    try (Connection first = connection();
        Connection second = connection()) {
      String firstToken = UUID.randomUUID().toString();
      String secondToken = UUID.randomUUID().toString();
      String sql =
          "UPDATE investory.notification_event SET delivery_state='PROCESSING', processing_token=?, processing_lease_until=?, attempt_count=attempt_count+1 "
              + "WHERE id=? AND attempt_count < 5 AND delivery_state='PENDING' AND next_attempt_at <= ?";
      CountDownLatch ready = new CountDownLatch(2);
      CountDownLatch start = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(2);
      Future<Integer> firstClaim =
          executor.submit(() -> claim(first, sql, id, firstToken, ready, start));
      Future<Integer> secondClaim =
          executor.submit(() -> claim(second, sql, id, secondToken, ready, start));
      ready.await();
      start.countDown();
      int firstResult = firstClaim.get();
      int secondResult = secondClaim.get();
      assertEquals(1, firstResult + secondResult);
      assertNotEquals(firstResult, secondResult);
      assertEquals(1, attemptCount(id));
      executor.shutdownNow();
    } finally {
      try (Connection cleanup = connection();
          var statement =
              cleanup.prepareStatement("DELETE FROM investory.notification_event WHERE id=?")) {
        statement.setLong(1, id);
        statement.executeUpdate();
        cleanup.commit();
      }
    }
  }

  @Test
  void expiredLeaseIsReclaimedButActiveAndExhaustedEventsAreNot() throws Exception {
    long id = insertEvent();
    try (Connection connection = connection();
        var claim =
            connection.prepareStatement(
                "UPDATE investory.notification_event SET delivery_state='PROCESSING', processing_token=?, processing_lease_until=?, attempt_count=attempt_count+1 WHERE id=? AND attempt_count < 5 AND ((delivery_state IN ('PENDING','RETRYABLE') AND next_attempt_at <= ?) OR (delivery_state='PROCESSING' AND processing_lease_until <= ?))")) {
      Instant now = Instant.now();
      claim.setString(1, "owner");
      claim.setTimestamp(2, Timestamp.from(now.plusSeconds(300)));
      claim.setLong(3, id);
      claim.setTimestamp(4, Timestamp.from(now.plusSeconds(1)));
      claim.setTimestamp(5, Timestamp.from(now));
      assertEquals(1, claim.executeUpdate());
      connection.commit();
    }
    assertEquals(1, attemptCount(id));
    try (Connection connection = connection()) {
      assertEquals(0, claim(connection, id, "stealer", Instant.now()));
      connection.rollback();
    }
    try (Connection connection = connection();
        var update =
            connection.prepareStatement(
                "UPDATE investory.notification_event SET processing_lease_until=? WHERE id=?")) {
      update.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(1)));
      update.setLong(2, id);
      update.executeUpdate();
      connection.commit();
    }
    try (Connection connection = connection()) {
      assertEquals(1, claim(connection, id, "reclaimer", Instant.now()));
      connection.commit();
    }
    assertEquals(2, attemptCount(id));
    try (Connection connection = connection();
        var update =
            connection.prepareStatement(
                "UPDATE investory.notification_event SET delivery_state='EXHAUSTED', attempt_count=5 WHERE id=?")) {
      update.setLong(1, id);
      update.executeUpdate();
      connection.commit();
    }
    try (Connection connection = connection()) {
      assertEquals(0, claim(connection, id, "after-exhaustion", Instant.now()));
      connection.rollback();
    }
    assertEquals(5, attemptCount(id));
    cleanupEvent(id);
  }

  private static int claim(
      Connection connection,
      String sql,
      long id,
      String token,
      CountDownLatch ready,
      CountDownLatch start)
      throws Exception {
    ready.countDown();
    start.await();
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, token);
      statement.setTimestamp(2, Timestamp.from(Instant.now().plusSeconds(300)));
      statement.setLong(3, id);
      statement.setTimestamp(4, Timestamp.from(Instant.now().plusSeconds(1)));
      int result = statement.executeUpdate();
      connection.commit();
      return result;
    }
  }

  private static int claim(Connection connection, long id, String token, Instant now)
      throws Exception {
    try (var statement =
        connection.prepareStatement(
            "UPDATE investory.notification_event SET delivery_state='PROCESSING', processing_token=?, processing_lease_until=?, attempt_count=attempt_count+1 WHERE id=? AND attempt_count < 5 AND ((delivery_state IN ('PENDING','RETRYABLE') AND next_attempt_at <= ?) OR (delivery_state='PROCESSING' AND processing_lease_until <= ?))")) {
      statement.setString(1, token);
      statement.setTimestamp(2, Timestamp.from(now.plusSeconds(300)));
      statement.setLong(3, id);
      statement.setTimestamp(4, Timestamp.from(now));
      statement.setTimestamp(5, Timestamp.from(now));
      return statement.executeUpdate();
    }
  }

  private static long insertEvent() throws Exception {
    try (Connection connection = connection();
        var statement =
            connection.prepareStatement(
                "INSERT INTO investory.notification_event(event_type, severity, source_entity_type, source_entity_id, fingerprint, title, payload, created_at, next_attempt_at) VALUES ('IMPORT_FAILED_OR_PARTIAL','ERROR','TEST','claim',?,?, '{}'::jsonb, now(), now()) RETURNING id")) {
      statement.setString(1, "claim:" + UUID.randomUUID());
      statement.setString(2, "claim");
      try (var result = statement.executeQuery()) {
        result.next();
        long id = result.getLong(1);
        connection.commit();
        return id;
      }
    }
  }

  private static Connection connection() throws Exception {
    Connection connection =
        DriverManager.getConnection(
            FastDatabase.jdbcUrl(), FastDatabase.username(), FastDatabase.password());
    connection.setAutoCommit(false);
    return connection;
  }

  private static void cleanupEvent(long id) throws Exception {
    try (Connection connection = connection();
        var statement =
            connection.prepareStatement("DELETE FROM investory.notification_event WHERE id=?")) {
      statement.setLong(1, id);
      statement.executeUpdate();
      connection.commit();
    }
  }

  private static int attemptCount(long id) throws Exception {
    try (Connection connection = connection();
        var statement =
            connection.prepareStatement(
                "SELECT attempt_count FROM investory.notification_event WHERE id=?")) {
      statement.setLong(1, id);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getInt(1);
      }
    }
  }
}
