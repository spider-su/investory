package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.contract.RetirementAnalysisContracts.AnalysisParameters;
import com.smartbox.investory.retirement.rest.RetirementAnalysisRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.retirement.analysis.RetirementAnalysisClient;
import com.smartbox.investory.ui.retirement.analysis.RetirementAnalysisView;
import org.springframework.stereotype.Component;

@Component
public class InProcessRetirementAnalysisClient implements RetirementAnalysisClient {
  private final RetirementAnalysisRestController rest;

  public InProcessRetirementAnalysisClient(RetirementAnalysisRestController rest) {
    this.rest = rest;
  }

  public RetirementAnalysisView analyze(
      Long portfolioId,
      Long planId,
      Integer defaultCurrentAge,
      Integer defaultEndAge,
      CurrencyType displayCurrency) {
    var response =
        rest.analyze(
            portfolioId,
            new AnalysisParameters(planId, defaultCurrentAge, defaultEndAge, displayCurrency));
    return new RetirementAnalysisView(
        response.available(),
        response.displaySummaries(),
        response.displayRisk(),
        response.displayFlexibility(),
        response.displayCharts());
  }
}
