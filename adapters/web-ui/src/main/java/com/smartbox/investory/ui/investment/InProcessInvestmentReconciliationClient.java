package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.InvestmentReconciliationApi;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationReport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentReconciliationClient implements InvestmentReconciliationClient {
  private final InvestmentReconciliationApi investmentReconciliationApi;

  public InProcessInvestmentReconciliationClient(
      @Qualifier("investmentReconciliationApplicationService")
          InvestmentReconciliationApi investmentReconciliationApi) {
    this.investmentReconciliationApi = investmentReconciliationApi;
  }

  @Override
  public ReconciliationReport loadReconciliationReport() {
    return investmentReconciliationApi.loadReconciliationReport();
  }
}
