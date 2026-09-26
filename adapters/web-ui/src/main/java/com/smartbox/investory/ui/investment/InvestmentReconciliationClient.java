package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;

/** UI boundary for the in-process investment reconciliation endpoint. */
public interface InvestmentReconciliationClient {
  ReconciliationReport loadReconciliationReport(Long portfolioId);

  void refreshReconciliationViews(Long portfolioId);
}
