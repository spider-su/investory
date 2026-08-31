package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsRefreshServiceTest {

  @Mock private PortfolioProjectionService portfolioProjectionService;

  @Test
  void refreshAllRefreshesPersistedPortfolioProjections() {
    StatisticsRefreshService service = new StatisticsRefreshService(portfolioProjectionService);

    service.refreshAll();

    InOrder ordered = inOrder(portfolioProjectionService);
    ordered.verify(portfolioProjectionService).recalculateAll();
    verifyNoMoreInteractions(portfolioProjectionService);
  }

  @Test
  void refreshAfterCommittedMutationUsesAnIndependentProjectionTransaction() {
    StatisticsRefreshService service = new StatisticsRefreshService(portfolioProjectionService);

    service.refreshAllAfterCommittedMutation();

    InOrder ordered = inOrder(portfolioProjectionService);
    ordered.verify(portfolioProjectionService).recalculateAllInNewTransaction();
    verifyNoMoreInteractions(portfolioProjectionService);
  }

  @Test
  void refreshFailureEscapesSoCallersCannotReportSuccess() {
    StatisticsRefreshService service = new StatisticsRefreshService(portfolioProjectionService);
    doThrow(new IllegalStateException("projection failed"))
        .when(portfolioProjectionService)
        .recalculateAll();

    assertThatThrownBy(service::refreshAll)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("projection failed");
  }
}
