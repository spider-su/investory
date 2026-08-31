package com.smartbox.investory.investment.projection;

import com.smartbox.investory.investment.performance.InvestmentCalculationCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Refreshes computed account statistics and materialized views after data mutations. */
@Slf4j
@Service
public class StatisticsRefreshService {

  private final PortfolioProjectionService portfolioProjectionService;
  private final InvestmentCalculationCache calculationCache;

  public StatisticsRefreshService(
      PortfolioProjectionService portfolioProjectionService,
      InvestmentCalculationCache calculationCache) {
    this.portfolioProjectionService = portfolioProjectionService;
    this.calculationCache = calculationCache;
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
    } finally {
      calculationCache.invalidate();
    }
  }
}
