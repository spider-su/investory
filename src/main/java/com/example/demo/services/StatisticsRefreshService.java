package com.example.demo.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Refreshes computed account statistics and materialized views after data mutations. */
@Slf4j
@Service
public class StatisticsRefreshService {

  private final JdbcTemplate jdbcTemplate;

  public StatisticsRefreshService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void refreshAll() {
    log.info("Refreshing account statistics and materialized views...");
    try {
      jdbcTemplate.execute("SELECT refresh_account_statistics()");
      jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_portfolio_asset_allocation");
      jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_portfolio_currency_breakdown");
      jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_portfolio_kpi_summary");
      log.info("Statistics refresh complete.");
    } catch (Exception e) {
      log.warn("Statistics refresh failed (non-fatal): {}", e.getMessage());
    }
  }
}
