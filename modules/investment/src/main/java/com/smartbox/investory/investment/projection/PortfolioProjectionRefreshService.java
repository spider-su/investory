package com.smartbox.investory.investment.projection;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Refreshes the database views used by portfolio reconciliation and reporting. */
@Slf4j
@Service
public class PortfolioProjectionRefreshService {
  private static final int ADVISORY_LOCK_KEY_1 = 2_147_483_647;
  private static final int ADVISORY_LOCK_KEY_2 = 1_001;

  private static final List<String> APPLICATION_FULL_ORDER =
      List.of(
          "app_v_canonical_asset_daily_price_mv",
          "app_v_canonical_asset_daily_price_ranked_mv",
          "app_v_normalized_daily_price_mv",
          "app_v_current_asset_price_mv",
          "app_v_portfolio_daily_fx_rate_mv",
          "app_v_normalized_cash_operations",
          "app_v_account_monthly",
          "app_v_portfolio_monthly",
          "app_v_account_statistics",
          "app_v_portfolio_contribution_summary_mv",
          "app_v_portfolio_currency_breakdown",
          "app_v_portfolio_asset_allocation",
          "app_v_symbol_performance",
          "app_v_portfolio_kpi_summary_mv");

  private static final List<String> CURRENT_MARKET_PRICE_ORDER =
      List.of(
          "app_v_canonical_asset_daily_price_mv",
          "app_v_canonical_asset_daily_price_ranked_mv",
          "app_v_current_asset_price_mv",
          "app_v_account_statistics",
          "app_v_portfolio_currency_breakdown",
          "app_v_portfolio_asset_allocation",
          "app_v_symbol_performance",
          "app_v_portfolio_kpi_summary_mv");

  private static final List<String> FX_ORDER =
      List.of(
          "app_v_portfolio_daily_fx_rate_mv",
          "app_v_normalized_cash_operations",
          "app_v_account_monthly",
          "app_v_portfolio_monthly",
          "app_v_account_statistics",
          "app_v_portfolio_contribution_summary_mv",
          "app_v_portfolio_currency_breakdown",
          "app_v_portfolio_asset_allocation",
          "app_v_symbol_performance",
          "app_v_portfolio_kpi_summary_mv");

  private static final List<String> DASHBOARD_ORDER =
      List.of(
          "app_v_portfolio_daily_fx_rate_mv",
          "app_v_normalized_cash_operations",
          "app_v_account_monthly",
          "app_v_portfolio_monthly",
          "app_v_account_statistics",
          "app_v_portfolio_currency_breakdown",
          "app_v_portfolio_asset_allocation",
          "app_v_symbol_performance",
          "app_v_portfolio_kpi_summary_mv");

  private static final List<String> RECONCILIATION_ORDER =
      List.of(
          "recon_v_reconstructed_position_daily_mv",
          "recon_v_reconstructed_account_market_daily_mv",
          "recon_v_reconstructed_cash_daily_mv",
          "recon_v_account_daily_reconciliation_mv",
          "recon_v_account_monthly_profit",
          "recon_v_account_statistics_vs_daily",
          "recon_v_account_daily_cashflow",
          "recon_v_account_daily_cashflow_scope",
          "recon_v_trade_settlement");

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public PortfolioProjectionRefreshService(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void refreshApplicationViews(ApplicationRefreshScope scope) {
    List<String> views =
        switch (scope) {
          case BROKER_IMPORT, MARKET_HISTORY, FULL -> APPLICATION_FULL_ORDER;
          case CURRENT_MARKET_PRICE -> CURRENT_MARKET_PRICE_ORDER;
          case FX_UPDATE -> FX_ORDER;
          case DASHBOARD -> DASHBOARD_ORDER;
        };
    refreshViews("application", views);
  }

  public void refreshReconciliationViews() {
    refreshViews("reconciliation", RECONCILIATION_ORDER);
  }

  private void refreshViews(String plan, List<String> views) {
    for (String view : views) {
      refreshOne(plan, view, !"reconciliation".equals(plan));
    }
  }

  private void refreshOne(String plan, String view, boolean concurrently) {
    long started = System.nanoTime();
    log.info("MV refresh started plan={} view={}", plan, view);
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock("
                    + ADVISORY_LOCK_KEY_1
                    + ", "
                    + ADVISORY_LOCK_KEY_2
                    + ")");
            jdbcTemplate.execute("SET LOCAL jit=off");
            jdbcTemplate.execute(
                "REFRESH MATERIALIZED VIEW "
                    + (concurrently ? "CONCURRENTLY " : "")
                    + "investory."
                    + view);
            jdbcTemplate.execute("ANALYZE investory." + view);
          });
      log.info(
          "MV refresh completed plan={} view={} durationMs={}",
          plan,
          view,
          (System.nanoTime() - started) / 1_000_000);
    } catch (RuntimeException exception) {
      log.error(
          "MV refresh failed plan={} view={} durationMs={}",
          plan,
          view,
          (System.nanoTime() - started) / 1_000_000,
          exception);
      throw exception;
    }
  }

  public enum ApplicationRefreshScope {
    BROKER_IMPORT,
    CURRENT_MARKET_PRICE,
    FX_UPDATE,
    DASHBOARD,
    MARKET_HISTORY,
    FULL
  }
}
