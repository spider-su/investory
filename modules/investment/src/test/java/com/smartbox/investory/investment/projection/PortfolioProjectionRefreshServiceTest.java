package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("Portfolio Projection Refresh Service")
class PortfolioProjectionRefreshServiceTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private TransactionStatus transactionStatus;

  @Test
  @DisplayName("FX refresh publishes the ordered bundle in one transaction")
  void fxRefreshPublishesOrderedBundleInOneTransaction() {
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    doNothing().when(jdbcTemplate).execute(any(String.class));

    PortfolioProjectionRefreshService service =
        new PortfolioProjectionRefreshService(jdbcTemplate, transactionManager);

    service.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.FX_UPDATE);

    verify(transactionManager, times(1)).getTransaction(any());
    verify(transactionManager, times(1)).commit(transactionStatus);
    verify(jdbcTemplate, times(1)).execute("SET LOCAL jit=off");
    InOrder ordered = inOrder(jdbcTemplate);
    ordered.verify(jdbcTemplate).execute("SET LOCAL jit=off");
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_daily_fx_rate_mv");
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_normalized_cash_operations");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_kpi_summary_mv");
  }

  @Test
  @DisplayName("projection prerequisites refresh price and FX chains before normalized cash")
  void projectionPrerequisitesRefreshPriceAndFxChainsBeforeNormalizedCash() {
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    doNothing().when(jdbcTemplate).execute(any(String.class));

    PortfolioProjectionRefreshService service =
        new PortfolioProjectionRefreshService(jdbcTemplate, transactionManager);

    service.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.PROJECTION_PREREQUISITES);

    verify(transactionManager, times(1)).getTransaction(any());
    verify(transactionManager, times(1)).commit(transactionStatus);
    verify(jdbcTemplate, times(1)).execute("SET LOCAL jit=off");
    InOrder ordered = inOrder(jdbcTemplate);
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_canonical_asset_daily_price_mv");
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_canonical_asset_daily_price_ranked_mv");
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_normalized_daily_price_mv");
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_daily_fx_rate_mv");
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_normalized_cash_operations");
  }

  @Test
  @DisplayName("dashboard refreshes price and reporting dependencies in order")
  void dashboardRefreshesPriceAndReportingDependenciesInOrder() {
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    doNothing().when(jdbcTemplate).execute(any(String.class));

    PortfolioProjectionRefreshService service =
        new PortfolioProjectionRefreshService(jdbcTemplate, transactionManager);

    service.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.DASHBOARD);

    verify(transactionManager, times(1)).getTransaction(any());
    verify(jdbcTemplate, times(0))
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_contribution_summary_mv");
    InOrder ordered = inOrder(jdbcTemplate);
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_canonical_asset_daily_price_mv");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_current_asset_price_mv");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_account_statistics");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_kpi_summary_mv");
  }

  @Test
  void currentPricesRefreshFxBeforeAccountsAndCommitAfterKpi() {
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    var service = new PortfolioProjectionRefreshService(jdbcTemplate, transactionManager);
    service.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.CURRENT_MARKET_PRICE);
    InOrder ordered = inOrder(jdbcTemplate, transactionManager);
    ordered
        .verify(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_daily_fx_rate_mv");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_account_statistics");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_kpi_summary_mv");
    ordered.verify(transactionManager).commit(transactionStatus);
    verify(transactionManager).getTransaction(any());
  }

  @Test
  void failedRefreshRollsBackBundleWithoutPublishingKpi() {
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    doNothing().when(jdbcTemplate).execute(any(String.class));
    doThrow(new IllegalStateException("FX refresh failed"))
        .when(jdbcTemplate)
        .execute(
            "REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_daily_fx_rate_mv");
    var service = new PortfolioProjectionRefreshService(jdbcTemplate, transactionManager);
    assertThatThrownBy(
            () ->
                service.refreshApplicationViews(
                    PortfolioProjectionRefreshService.ApplicationRefreshScope.CURRENT_MARKET_PRICE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("FX refresh failed");
    verify(transactionManager).rollback(transactionStatus);
    verify(transactionManager, never()).commit(any());
    verify(jdbcTemplate, never())
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY investory.app_v_portfolio_kpi_summary_mv");
  }
}
