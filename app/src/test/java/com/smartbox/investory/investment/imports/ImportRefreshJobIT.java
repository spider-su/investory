package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.notifications.application.PersistentNotificationEventPublisher;
import com.smartbox.investory.investment.infrastructure.persistence.imports.*;
import com.smartbox.investory.shared.notifications.*;
import com.smartbox.investory.testsupport.FastDatabase;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;

class ImportRefreshJobIT {
  private DriverManagerDataSource source;
  private JdbcTemplate jdbc;
  private ImportRepository imports;
  private ImportOrchestratorService runner;
  private ImportRefreshService jobs;
  private long batchId;
  private long portfolioId;

  @BeforeEach
  void setup() {
    source =
        new DriverManagerDataSource(
            FastDatabase.jdbcUrl(), FastDatabase.username(), FastDatabase.password());
    jdbc = new JdbcTemplate(source);
    imports = mock(ImportRepository.class);
    runner = mock(ImportOrchestratorService.class);
    portfolioId =
        jdbc.queryForObject("SELECT id FROM investory.portfolios ORDER BY id LIMIT 1", Long.class);
    batchId =
        jdbc.queryForObject(
            "INSERT INTO investory.import_history(provider,portfolio_id,file_name,file_sha256,started_at,finished_at,status,rows_total,rows_applied,rows_failed) VALUES('IBKR',?,'synthetic.csv',?,now(),now(),'COMPLETED',3,3,0) RETURNING id",
            Long.class,
            portfolioId,
            UUID.randomUUID().toString().replace("-", "").repeat(2));
    var batch = new ImportHistoryEntity();
    batch.setId(batchId);
    batch.setPortfolioId(portfolioId);
    batch.setBroker(BrokerType.IBKR);
    batch.setRowsTotal(3);
    batch.setRowsApplied(3);
    batch.setRowsFailed(0);
    when(imports.findById(batchId)).thenReturn(Optional.of(batch));
    var tx = new DataSourceTransactionManager(source);
    // Publish inside the same physical transaction as the final job-state update.
    var publisher = new PersistentNotificationEventPublisher(jdbc, new ObjectMapper());
    jobs = new ImportRefreshService(jdbc, source, imports, runner, publisher, tx);
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM investory.notification_event WHERE source_entity_type='IMPORT_HISTORY' AND source_entity_id=?",
        Long.toString(batchId));
    jdbc.update("DELETE FROM investory.import_refresh_job WHERE batch_id=?", batchId);
    jdbc.update("DELETE FROM investory.import_history WHERE id=?", batchId);
  }

  @Test
  void enqueueIsIdempotentAndSuccessIsDurable() {
    jobs.enqueue(portfolioId, batchId, Set.of());
    jobs.enqueue(portfolioId, batchId, Set.of());
    assertEquals("PENDING", jobs.status(portfolioId, batchId).get("status"));
    jobs.processNext();
    assertEquals("COMPLETED", jobs.status(portfolioId, batchId).get("status"));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM investory.notification_event WHERE fingerprint=?",
            Integer.class,
            "IMPORT_REFRESH_FINISHED:" + batchId + ":COMPLETED"));
    jobs.processNext();
    verify(runner, times(1)).refreshImportedBatch(any(), any());
  }

  @Test
  void failedRefreshRetainsSavedImportAndPublishesFailure() {
    jobs.enqueue(portfolioId, batchId, Set.of());
    doThrow(new IllegalStateException("synthetic failure"))
        .when(runner)
        .refreshImportedBatch(any(), any());
    jobs.processNext();
    assertEquals("FAILED", jobs.status(portfolioId, batchId).get("status"));
    assertEquals(
        "COMPLETED",
        jdbc.queryForObject(
            "SELECT status FROM investory.import_history WHERE id=?", String.class, batchId));
    assertEquals(
        "ERROR",
        jdbc.queryForObject(
            "SELECT severity FROM investory.notification_event WHERE fingerprint=?",
            String.class,
            "IMPORT_REFRESH_FINISHED:" + batchId + ":FAILED"));
  }

  @Test
  void abandonedRunningJobIsRecovered() {
    jobs.enqueue(portfolioId, batchId, Set.of());
    jdbc.update(
        "UPDATE investory.import_refresh_job SET status='RUNNING' WHERE batch_id=?", batchId);
    jobs.processNext();
    assertEquals("COMPLETED", jobs.status(portfolioId, batchId).get("status"));
  }

  @Test
  void anotherPortfolioCannotReadOrEnqueueBatch() {
    assertThrows(
        RuntimeException.class, () -> jobs.enqueue(portfolioId + 1000000, batchId, Set.of()));
    jobs.enqueue(portfolioId, batchId, Set.of());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> jobs.status(portfolioId + 1000000, batchId));
  }

  @Test
  void rejectsAccountsOutsideThePortfolio() {
    assertThrows(
        IllegalArgumentException.class,
        () -> jobs.enqueue(portfolioId, batchId, Set.of(Long.MAX_VALUE)));
    verifyNoInteractions(runner);
  }

  @Test
  void persistsExactParserScopeForRestartRecovery() {
    Long account =
        jdbc.queryForObject(
            "SELECT min(id) FROM investory.accounts WHERE portfolio_id=?", Long.class, portfolioId);
    assertNotNull(account);
    jobs.enqueue(portfolioId, batchId, Set.of(account));
    jobs.processNext();
    verify(runner).refreshImportedBatch(any(), eq(Set.of(account)));
  }

  @Test
  void workerRechecksAccountOwnershipBeforeRefresh() {
    jobs.enqueue(portfolioId, batchId, Set.of());
    jdbc.update(
        "UPDATE investory.import_refresh_job SET affected_account_ids='[9223372036854775807]'::jsonb WHERE batch_id=?",
        batchId);
    jobs.processNext();
    assertEquals("FAILED", jobs.status(portfolioId, batchId).get("status"));
    verifyNoInteractions(runner);
  }

  @Test
  void heldSessionLockPreventsDoubleProcessing() throws Exception {
    jobs.enqueue(portfolioId, batchId, Set.of());
    try (var lock = source.getConnection();
        var st = lock.createStatement()) {
      st.execute("SELECT pg_advisory_lock(728410613)");
      try {
        jobs.processNext();
        assertEquals("PENDING", jobs.status(portfolioId, batchId).get("status"));
        verifyNoInteractions(runner);
      } finally {
        st.execute("SELECT pg_advisory_unlock(728410613)");
      }
    }
  }

  @Test
  void notificationFailureRollsBackTerminalState() {
    jobs.enqueue(portfolioId, batchId, Set.of());
    var broken =
        new ImportRefreshService(
            jdbc,
            source,
            imports,
            runner,
            c -> {
              throw new IllegalStateException("synthetic outbox failure");
            },
            new DataSourceTransactionManager(source));
    broken.processNext();
    assertEquals("RUNNING", jobs.status(portfolioId, batchId).get("status"));
    jobs.processNext();
    assertEquals("COMPLETED", jobs.status(portfolioId, batchId).get("status"));
  }
}
