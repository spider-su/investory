package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-process adapter for retirement presentation operations. */
@Component
public class InProcessRetirementPresentationClient implements RetirementPresentationClient {
  private final RetirementPresentationApi api;

  public InProcessRetirementPresentationClient(
      @Qualifier("retirementPlanningApplicationService") RetirementPresentationApi api) {
    this.api = api;
  }

  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return api.toDisplay(canonical, display);
  }

  public BigDecimal fromDisplay(BigDecimal amount, CurrencyType display, BigDecimal fallback) {
    return api.fromDisplay(amount, display, fallback);
  }

  public PastPlanningYear display(PastPlanningYear past, CurrencyType display) {
    return api.display(past, display);
  }

  public HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation reconciliation, CurrencyType display) {
    return api.displayReconciliation(reconciliation, display);
  }

  public PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display) {
    return api.displayProfile(profile, display);
  }

  public Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, CurrencyType display) {
    return api.displaySummaries(summaries, display);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency) {
    return api.displayTimelineMoney(timeline, currency);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency, SimulationAssumptions assumptions) {
    return api.displayTimelineMoney(timeline, currency, assumptions);
  }

  public PlanRiskView displayPlanRisks(
      SimulationSensitivityAnalysis analysis, CurrencyType display) {
    return api.displayPlanRisks(analysis, display);
  }

  public PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return api.displayPlanningFlexibility(spending, retirement, display);
  }

  public SimulationChartData displayCharts(SimulationChartData charts, CurrencyType display) {
    return api.displayCharts(charts, display);
  }
}
