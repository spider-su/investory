package com.smartbox.investory.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Refreshes computed account statistics and materialized views after data mutations. */
@Slf4j
@Service
public class StatisticsRefreshService {

  private final PortfolioProjectionService portfolioProjectionService;

  @Autowired(required = false)
  private InvestmentCalculationCache calculationCache;

  public StatisticsRefreshService(PortfolioProjectionService portfolioProjectionService) {
    this.portfolioProjectionService = portfolioProjectionService;
  }

  public void refreshAll() {
    refresh(portfolioProjectionService::recalculateAll);
  }

  /** Rebuilds after the calling transaction has committed, so the rebuild sees committed data. */
  public void refreshAllAfterCommittedMutation() {
    refresh(portfolioProjectionService::recalculateAllInNewTransaction);
  }

  private void refresh(Runnable recalculate) {
    log.info("Refreshing persisted portfolio projections...");
    try {
      recalculate.run();
      log.info("Projection refresh complete.");
    } catch (Exception e) {
      log.warn("Projection refresh failed (non-fatal): {}", e.getMessage());
    } finally {
      if (calculationCache != null) {
        calculationCache.invalidate();
      }
    }
  }
}
