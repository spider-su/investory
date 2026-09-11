package com.smartbox.investory.investment.projection;

import com.smartbox.investory.investment.performance.InvestmentCalculationCache;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Refreshes computed account statistics and materialized views after data mutations. */
@Slf4j
@Service
public class StatisticsRefreshService {

  private final PortfolioProjectionService portfolioProjectionService;
  private final PortfolioProjectionRefreshService projectionRefreshService;
  private final InvestmentCalculationCache calculationCache;

  public StatisticsRefreshService(
      PortfolioProjectionService portfolioProjectionService,
      PortfolioProjectionRefreshService projectionRefreshService,
      InvestmentCalculationCache calculationCache) {
    this.portfolioProjectionService = portfolioProjectionService;
    this.projectionRefreshService = projectionRefreshService;
    this.calculationCache = calculationCache;
  }

  public void refreshAll() {
    refresh(portfolioProjectionService::recalculateAll);
    projectionRefreshService.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.FULL);
  }

  /** Rebuilds after the calling transaction has committed, so the rebuild sees committed data. */
  public void refreshAllAfterCommittedMutation() {
    refresh(portfolioProjectionService::recalculateAllInNewTransaction);
    projectionRefreshService.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.FULL);
  }

  public void refreshCurrentMarketPrices() {
    projectionRefreshService.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.CURRENT_MARKET_PRICE);
    calculationCache.invalidate();
  }

  /** Rebuilds only the affected accounts and invalidates only their portfolio cache. */
  public void refreshAffectedAccounts(Long portfolioId, Set<Long> accountIds) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    if (accountIds == null || accountIds.isEmpty()) {
      return;
    }
    log.info(
        "Refreshing scoped portfolio projections portfolioId={} accounts={}",
        portfolioId,
        accountIds);
    try {
      portfolioProjectionService.recalculateAccountsScoped(portfolioId, accountIds);
      projectionRefreshService.refreshApplicationViews(
          PortfolioProjectionRefreshService.ApplicationRefreshScope.BROKER_IMPORT);
    } finally {
      calculationCache.invalidatePortfolio(portfolioId);
    }
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
