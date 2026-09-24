package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Map;

/** UI seam for retirement presentation and currency conversion operations. */
public interface RetirementPresentationClient {
  BigDecimal toDisplay(BigDecimal canonical, CurrencyType display);

  BigDecimal fromDisplay(BigDecimal amount, CurrencyType display, BigDecimal fallback);

  PastPlanningYear display(PastPlanningYear past, CurrencyType display);

  HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation reconciliation, CurrencyType display);

  PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display);

  Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, CurrencyType display);

  Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency);

  Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency, SimulationAssumptions assumptions);

  PlanRiskView displayPlanRisks(SimulationSensitivityAnalysis analysis, CurrencyType display);

  PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending, RetirementAgeAnalysis retirement, CurrencyType display);

  SimulationChartData displayCharts(SimulationChartData charts, CurrencyType display);
}
