package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.smartbox.investory.rag.AssistantConversationService;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

class AssistantConversationIT {
  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("assistant_conversation");
  private static DriverManagerDataSource source;
  private JdbcTemplate jdbc;
  private AssistantConversationService service;

  @BeforeAll
  static void migrate() {
    MigrationTestDatabase.migrate(DATABASE);
    source =
        new DriverManagerDataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password());
  }

  @AfterAll
  static void close() {
    DATABASE.close();
  }

  @BeforeEach
  void prepare() {
    jdbc = new JdbcTemplate(source);
    jdbc.update("DELETE FROM investory.assistant_scenario_revision");
    jdbc.update("DELETE FROM investory.assistant_conversation");
    service = new AssistantConversationService(jdbc, new DataSourceTransactionManager(source));
  }

  @Test
  void stateSurvivesServiceRestartAndIsScopedToOwnerAndPortfolio() {
    var session = service.create(21, "alice");
    var persisted = service.updateContext(session, "PORTFOLIO_PROFIT", null, "MAX", null);
    var restarted =
        new AssistantConversationService(
            new JdbcTemplate(source), new DataSourceTransactionManager(source));
    assertEquals("PORTFOLIO_PROFIT", restarted.get(session.id(), 21, "alice").topic());
    assertEquals(1L, persisted.version());
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException.class, () -> restarted.get(session.id(), 21, "bob"))
            .getStatusCode());
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException.class, () -> restarted.get(session.id(), 22, "alice"))
            .getStatusCode());
  }

  @Test
  void staleUpdateFailsInsteadOfOverwritingContext() {
    var original = service.create(21, "alice");
    service.updateContext(original, "PORTFOLIO_PROFIT", null, "MAX", null);
    var error =
        assertThrows(
            ResponseStatusException.class,
            () -> service.updateContext(original, "SPY_BENCHMARK", null, "YTD", null));
    assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    assertEquals("PORTFOLIO_PROFIT", service.get(original.id(), 21, "alice").topic());
  }

  @Test
  void expiredConversationCannotBeReadOrChanged() {
    var session = service.create(21, "alice");
    jdbc.update(
        "UPDATE investory.assistant_conversation SET expires_at=? WHERE id=?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
        session.id());
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException.class, () -> service.get(session.id(), 21, "alice"))
            .getStatusCode());
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(
                ResponseStatusException.class,
                () -> service.saveScenario(session, "Base", BigDecimal.ONE, BigDecimal.ZERO))
            .getStatusCode());
  }

  @Test
  void scenarioRevisionsAreAtomicAndDoNotModifyOtherSessions() throws Exception {
    var session = service.create(21, "alice");
    var other = service.create(21, "bob");
    try (var pool = Executors.newFixedThreadPool(4)) {
      var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
      for (int n = 0; n < 8; n++) {
        tasks.add(
            pool.submit(
                () ->
                    service
                        .saveScenario(
                            session, "Base", BigDecimal.valueOf(180000), BigDecimal.valueOf(12000))
                        .revision()));
      }
      var revisions = new java.util.HashSet<Integer>();
      for (var task : tasks) revisions.add(task.get());
      assertEquals(8, revisions.size());
    }
    assertEquals(8, service.revisions(session).size());
    assertTrue(service.revisions(other).isEmpty());
    assertEquals(
        HttpStatus.NOT_FOUND,
        assertThrows(ResponseStatusException.class, () -> service.get(session.id(), 21, "bob"))
            .getStatusCode());
  }

  @Test
  void invalidExpensesAreRejectedWithoutPersistingScenario() {
    var session = service.create(21, "alice");
    assertEquals(
        HttpStatus.BAD_REQUEST,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    service.saveScenario(session, "Base", BigDecimal.ONE.negate(), BigDecimal.ZERO))
            .getStatusCode());
    assertTrue(service.revisions(session).isEmpty());
  }
}
