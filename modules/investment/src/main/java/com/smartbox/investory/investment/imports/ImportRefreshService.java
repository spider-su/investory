package com.smartbox.investory.investment.imports;

import com.smartbox.investory.investment.infrastructure.persistence.imports.ImportRepository;
import com.smartbox.investory.shared.notifications.*;
import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Durable serial refresh queue. A PostgreSQL session lock also protects against multiple pods. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportRefreshService {
  private final JdbcTemplate jdbc;
  private final DataSource dataSource;
  private final ImportRepository imports;
  private final ImportOrchestratorService orchestrator;
  private final NotificationEventPublisher notifications;
  private final PlatformTransactionManager transactions;
  private static final long LOCK_KEY = 728410613L;

  public void enqueue(Long portfolioId, Long batchId, Set<Long> affectedAccounts) {
    String scope = validateScope(portfolioId, affectedAccounts);
    int inserted =
        jdbc.update(
            """
        INSERT INTO investory.import_refresh_job(batch_id,portfolio_id,affected_account_ids)
        SELECT id,portfolio_id,?::jsonb FROM investory.import_history
        WHERE id=? AND portfolio_id=? AND status IN ('COMPLETED','PARTIAL')
        ON CONFLICT(batch_id) DO NOTHING
        """,
            scope,
            batchId,
            portfolioId);
    if (inserted == 0
        && jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_refresh_job WHERE batch_id=? AND portfolio_id=?",
                Integer.class,
                batchId,
                portfolioId)
            == 0) throw new IllegalArgumentException("Applied import batch not found in portfolio");
  }

  private String validateScope(Long portfolioId, Set<Long> affectedAccounts) {
    String scope = Set.copyOf(affectedAccounts).toString();
    if (jdbc.queryForObject(
            "SELECT count(*) FROM investory.accounts WHERE portfolio_id=? AND id IN (SELECT value::bigint FROM jsonb_array_elements_text(?::jsonb))",
            Integer.class,
            portfolioId,
            scope)
        != affectedAccounts.size())
      throw new IllegalArgumentException("Refresh account does not belong to portfolio");
    return scope;
  }

  public Map<String, Object> status(Long portfolioId, Long batchId) {
    var rows =
        jdbc.queryForList(
            "SELECT batch_id,status,started_at,finished_at FROM investory.import_refresh_job WHERE batch_id=? AND portfolio_id=?",
            batchId,
            portfolioId);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Refresh job not found");
    return rows.getFirst();
  }

  public void processNext() {
    try (Connection lock = dataSource.getConnection()) {
      try (var statement = lock.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
        statement.setLong(1, LOCK_KEY);
        try (var result = statement.executeQuery()) {
          result.next();
          if (!result.getBoolean(1)) return;
        }
      }
      try {
        // A RUNNING job is recovered only after the previous worker's session lock is released.
        var jobs =
            jdbc.queryForList(
                "SELECT batch_id,portfolio_id FROM investory.import_refresh_job WHERE status IN ('PENDING','RUNNING') ORDER BY batch_id LIMIT 1");
        if (jobs.isEmpty()) return;
        var job = jobs.getFirst();
        long batchId = ((Number) job.get("batch_id")).longValue();
        long portfolioId = ((Number) job.get("portfolio_id")).longValue();
        jdbc.update(
            "UPDATE investory.import_refresh_job SET status='RUNNING',started_at=current_timestamp,finished_at=NULL WHERE batch_id=?",
            batchId);
        var batch = imports.findById(batchId).orElseThrow();
        if (!Objects.equals(batch.getPortfolioId(), portfolioId))
          throw new IllegalStateException("Refresh portfolio mismatch");
        String failure = null;
        try {
          var accounts =
              new HashSet<>(
                  jdbc.queryForList(
                      "SELECT value::bigint FROM investory.import_refresh_job j CROSS JOIN LATERAL jsonb_array_elements_text(j.affected_account_ids) a(value) WHERE j.batch_id=? AND j.portfolio_id=?",
                      Long.class,
                      batchId,
                      portfolioId));
          validateScope(portfolioId, accounts);
          orchestrator.refreshImportedBatch(batch, accounts);
        } catch (Exception exception) {
          failure = exception.getClass().getSimpleName();
          log.error(
              "Background import refresh failed batchId={} portfolioId={}",
              batchId,
              portfolioId,
              exception);
        }
        finish(batchId, portfolioId, batch.getBroker().name(), failure);
      } finally {
        try (var statement = lock.prepareStatement("SELECT pg_advisory_unlock(?)")) {
          statement.setLong(1, LOCK_KEY);
          statement.execute();
        } catch (Exception failure) {
          lock.abort(Runnable::run);
          throw failure;
        }
      }
    } catch (Exception failure) {
      log.error("Import refresh worker failed", failure);
    }
  }

  void finish(long batchId, long portfolioId, String broker, String failure) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              String state = failure == null ? "COMPLETED" : "FAILED";
              jdbc.update(
                  "UPDATE investory.import_refresh_job SET status=?,finished_at=current_timestamp,error=? WHERE batch_id=? AND portfolio_id=?",
                  state,
                  failure,
                  batchId,
                  portfolioId);
              notifications.publish(
                  new NotificationCandidate(
                      NotificationEventType.IMPORT_REFRESH_FINISHED,
                      failure == null ? NotificationSeverity.INFO : NotificationSeverity.ERROR,
                      portfolioId,
                      "IMPORT_HISTORY",
                      Long.toString(batchId),
                      "IMPORT_REFRESH_FINISHED:" + batchId + ":" + state,
                      failure == null ? "Import refresh completed" : "Import saved; refresh failed",
                      Map.of(
                          "importId",
                          Long.toString(batchId),
                          "broker",
                          broker,
                          "status",
                          state,
                          "failure",
                          failure == null ? "" : failure),
                      java.time.Instant.now()));
            });
  }
}
