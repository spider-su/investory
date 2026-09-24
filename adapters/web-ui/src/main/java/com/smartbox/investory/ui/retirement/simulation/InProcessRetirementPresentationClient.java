package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.rest.RetirementTimelineRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/** In-process adapter for retirement presentation operations. */
@Component
public class InProcessRetirementPresentationClient implements RetirementPresentationClient {
  private final RetirementTimelineRestController rest;

  public InProcessRetirementPresentationClient(RetirementTimelineRestController rest) {
    this.rest = rest;
  }

  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return rest.toDisplay(canonical, display);
  }

  public BigDecimal fromDisplay(BigDecimal amount, CurrencyType display, BigDecimal fallback) {
    return rest.fromDisplay(amount, display, fallback);
  }

  public PastPlanningYear display(PastPlanningYear past, CurrencyType display) {
    return rest.display(past, display);
  }

  public HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation reconciliation, CurrencyType display) {
    return rest.displayReconciliation(reconciliation, display);
  }

  public PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display) {
    return rest.displayProfile(profile, display);
  }

  public Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, CurrencyType display) {
    return rest.displaySummaries(summaries, display);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency) {
    return rest.displayTimelineMoney(timeline, currency);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency, SimulationAssumptions assumptions) {
    return rest.displayTimelineMoney(timeline, currency, assumptions);
  }

  public PlanRiskView displayPlanRisks(
      SimulationSensitivityAnalysis analysis, CurrencyType display) {
    return rest.displayPlanRisks(analysis, display);
  }

  public PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return rest.displayPlanningFlexibility(spending, retirement, display);
  }

  public SimulationChartData displayCharts(SimulationChartData charts, CurrencyType display) {
    return rest.displayCharts(charts, display);
  }
}
