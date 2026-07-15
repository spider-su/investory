package com.example.demo.services;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StatisticsRefreshServiceTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void refreshAllRefreshesAccountStatisticsAndMaterializedViews() {
    StatisticsRefreshService service = new StatisticsRefreshService(jdbcTemplate);

    service.refreshAll();

    InOrder ordered = inOrder(jdbcTemplate);
    ordered.verify(jdbcTemplate).execute("SELECT refresh_account_statistics()");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_portfolio_asset_allocation");
    ordered
        .verify(jdbcTemplate)
        .execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_portfolio_currency_breakdown");
    ordered.verify(jdbcTemplate).execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_portfolio_kpi_summary");
    verifyNoMoreInteractions(jdbcTemplate);
  }
}
