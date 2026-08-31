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

  @DisplayName("loads Current System Wide Report")
  @Test
  void loadsCurrentSystemWideReport() {
    var service = new InvestmentReconciliationApplicationService(reports);

    service.loadReconciliationReport();

    verify(reports).generateReport();
  }
}
