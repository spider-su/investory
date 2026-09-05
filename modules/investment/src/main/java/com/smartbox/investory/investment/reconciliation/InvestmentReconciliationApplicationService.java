package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.shared.time.ApplicationTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Adapts reconciliation reports to the public Investment API. */
@Service
@Primary
@RequiredArgsConstructor
public class InvestmentReconciliationApplicationService implements InvestmentReconciliationApi {
  private final ReconciliationReportService reconciliationReportService;
  private final PortfolioProjectionRefreshService projectionRefreshService;
  private final ApplicationTime applicationTime;

  @Override
  public ReconciliationReport loadReconciliationReport(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new IllegalArgumentException("portfolioId must be positive");
    }
    return reconciliationReportService.generateReport(
        new ReconciliationContext(applicationTime.now(), applicationTime.today(), portfolioId));
  }

  @Override
  public void refreshReconciliationViews() {
    projectionRefreshService.refreshReconciliationViews();
  }
}
