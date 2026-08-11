package com.example.demo.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Refreshes computed account statistics and materialized views after data mutations. */
@Slf4j
@Service
public class StatisticsRefreshService {

  private final PortfolioProjectionService portfolioProjectionService;

  public StatisticsRefreshService(PortfolioProjectionService portfolioProjectionService) {
    this.portfolioProjectionService = portfolioProjectionService;
  }

  public void refreshAll() {
    log.info("Refreshing persisted portfolio projections...");
    try {
      portfolioProjectionService.recalculateAll();
      log.info("Projection refresh complete.");
    } catch (Exception e) {
      log.warn("Projection refresh failed (non-fatal): {}", e.getMessage());
    }
  }
}
