package com.smartbox.investory.investment.api.reporting;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;

/** UI-facing reconciliation report boundary. */
public interface InvestmentReconciliationApi {
  ReconciliationReport loadReconciliationReport(Long portfolioId);

  void refreshReconciliationViews();
}
