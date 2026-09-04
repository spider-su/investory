package com.smartbox.investory.investment.projection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
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
  @DisplayName("FX refresh uses ordered independent transactions for each MV")
  void fxRefreshUsesOrderedIndependentTransactionsForEachMv() {
    when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    doNothing().when(jdbcTemplate).execute(any(String.class));

    PortfolioProjectionRefreshService service =
        new PortfolioProjectionRefreshService(jdbcTemplate, transactionManager);

    service.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.FX_UPDATE);

    verify(transactionManager, times(10)).getTransaction(any());
    verify(transactionManager, times(10)).commit(transactionStatus);
    verify(jdbcTemplate, times(10)).execute("SET LOCAL jit=off");
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
}
