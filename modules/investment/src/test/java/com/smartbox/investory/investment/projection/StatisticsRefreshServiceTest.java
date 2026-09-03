package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.smartbox.investory.investment.performance.InvestmentCalculationCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Statistics Refresh Service")
class StatisticsRefreshServiceTest {

  @Mock private PortfolioProjectionService portfolioProjectionService;
  @Mock private PortfolioProjectionRefreshService projectionRefreshService;
  @Mock private InvestmentCalculationCache calculationCache;

  @DisplayName("refresh All Refreshes Persisted Portfolio Projections")
  @Test
  void refreshAllRefreshesPersistedPortfolioProjections() {
    StatisticsRefreshService service =
        new StatisticsRefreshService(
            portfolioProjectionService, projectionRefreshService, calculationCache);

    service.refreshAll();

    InOrder ordered = inOrder(portfolioProjectionService);
    ordered.verify(portfolioProjectionService).recalculateAll();
    verifyNoMoreInteractions(portfolioProjectionService);
  }

  @DisplayName("refresh After Committed Mutation Uses An Independent Projection Transaction")
  @Test
  void refreshAfterCommittedMutationUsesAnIndependentProjectionTransaction() {
    StatisticsRefreshService service =
        new StatisticsRefreshService(
            portfolioProjectionService, projectionRefreshService, calculationCache);

    service.refreshAllAfterCommittedMutation();

    InOrder ordered = inOrder(portfolioProjectionService);
    ordered.verify(portfolioProjectionService).recalculateAllInNewTransaction();
    verifyNoMoreInteractions(portfolioProjectionService);
  }

  @DisplayName("refresh Failure Escapes So Callers Cannot Report Success")
  @Test
  void refreshFailureEscapesSoCallersCannotReportSuccess() {
    StatisticsRefreshService service =
        new StatisticsRefreshService(
            portfolioProjectionService, projectionRefreshService, calculationCache);
    doThrow(new IllegalStateException("projection failed"))
        .when(portfolioProjectionService)
        .recalculateAll();

    assertThatThrownBy(service::refreshAll)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("projection failed");
  }
}
