package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.accounting.PortfolioProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReconciliationRefreshService {

  private final PortfolioProjectionService portfolioProjectionService;
  private final JdbcTemplate jdbcTemplate;
  private final boolean enabled;

  @Autowired
  public ReconciliationRefreshService(
      PortfolioProjectionService portfolioProjectionService,
      JdbcTemplate jdbcTemplate,
      @Value("${app.import.reconciliation-refresh-enabled:false}") boolean enabled) {
    this.portfolioProjectionService = portfolioProjectionService;
    this.jdbcTemplate = jdbcTemplate;
    this.enabled = enabled;
  }

  /** Source-compatible constructor for focused refresh tests. */
  ReconciliationRefreshService(
      PortfolioProjectionService portfolioProjectionService, boolean enabled) {
    this(portfolioProjectionService, null, enabled);
  }

  @Async("reconciliationRefreshExecutor")
  public void refreshAfterImport(Long batchId) {
    long started = System.nanoTime();
    if (!enabled) {
      log.info(
          "IMPORT PERF reconciliation-refresh={}ms batchId={} skipped=disabled",
          (System.nanoTime() - started) / 1_000_000L,
          batchId);
      log.info(
          "Post-import system audit skipped because reconciliation refresh is disabled; batchId={}",
          batchId);
      return;
    }

    try {
      portfolioProjectionService.refreshReconciliationViews();
    } catch (Exception exception) {
      log.warn(
          "Reconciliation refresh failed after import (batchId={}): {}",
          batchId,
          exception.getMessage(),
          exception);
      return;
    } finally {
      log.info(
          "IMPORT PERF reconciliation-refresh={}ms batchId={}",
          (System.nanoTime() - started) / 1_000_000L,
          batchId);
    }

    runAuditAfterCommittedImport(batchId);
  }

  private void runAuditAfterCommittedImport(Long batchId) {
    if (jdbcTemplate == null) {
      return;
    }
    long started = System.nanoTime();
    try {
      jdbcTemplate.queryForObject(
          "SELECT investory.run_system_audit(?, 'POST_IMPORT')", java.util.UUID.class, batchId);
    } catch (Exception exception) {
      // Import data and its finalized status are already committed. Audit failures are operational
      // follow-up work and must never turn a successful import into a database rollback.
      log.warn(
          "Post-import system audit failed for batchId={}: {}",
          batchId,
          exception.getMessage(),
          exception);
    } finally {
      log.info(
          "IMPORT PERF system-audit={}ms batchId={}",
          (System.nanoTime() - started) / 1_000_000L,
          batchId);
    }
  }
}
