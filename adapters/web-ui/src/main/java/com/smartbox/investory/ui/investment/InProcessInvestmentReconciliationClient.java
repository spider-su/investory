package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import com.smartbox.investory.investment.web.InvestmentReconciliationRestController;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentReconciliationClient implements InvestmentReconciliationClient {
  private final InvestmentReconciliationRestController rest;

  public InProcessInvestmentReconciliationClient(InvestmentReconciliationRestController rest) {
    this.rest = rest;
  }

  @Override
  public ReconciliationReport loadReconciliationReport(Long portfolioId) {
    return rest.report(portfolioId);
  }

  @Override
  public void refreshReconciliationViews(Long portfolioId) {
    rest.refresh(portfolioId);
  }
}
