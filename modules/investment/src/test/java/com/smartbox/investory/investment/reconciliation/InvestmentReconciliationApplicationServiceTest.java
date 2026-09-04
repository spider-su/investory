package com.smartbox.investory.investment.reconciliation;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Investment Reconciliation Application Service")
class InvestmentReconciliationApplicationServiceTest {
  @Mock private ReconciliationReportService reports;

  @DisplayName("loads Portfolio Report")
  @Test
  void loadsPortfolioReport() {
    var service = new InvestmentReconciliationApplicationService(reports);

    service.loadReconciliationReport(7L);

    verify(reports).generateReport(org.mockito.ArgumentMatchers.any(ReconciliationContext.class));
  }

  @Test
  void rejectsMissingOrInvalidPortfolioId() {
    var service = new InvestmentReconciliationApplicationService(reports);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.loadReconciliationReport(null))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.loadReconciliationReport(0L))
        .isInstanceOf(IllegalArgumentException.class);
    org.mockito.Mockito.verifyNoInteractions(reports);
  }
}
