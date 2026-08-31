package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Adapts reconciliation reports to the public Investment API. */
@Service
@Primary
@RequiredArgsConstructor
public class InvestmentReconciliationApplicationService implements InvestmentReconciliationApi {
  private final ReconciliationReportService reconciliationReportService;

  @Override
  public ReconciliationReport loadReconciliationReport() {
    return reconciliationReportService.generateReport();
  }
}
