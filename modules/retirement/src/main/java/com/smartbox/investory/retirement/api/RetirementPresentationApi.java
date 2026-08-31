package com.smartbox.investory.retirement.api;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.Map;

/** Public boundary for currency-aware retirement presentation projections. */
public interface RetirementPresentationApi {
  BigDecimal toDisplay(BigDecimal canonical, CurrencyType display);

  BigDecimal fromDisplay(
      BigDecimal amount, CurrencyType display, BigDecimal fallbackCanonicalAmount);

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
