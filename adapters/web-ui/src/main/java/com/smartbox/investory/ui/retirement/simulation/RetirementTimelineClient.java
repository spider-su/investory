package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.contract.RetirementTimelineContracts;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;

/** UI seam for retirement planning timeline queries and commands. */
public interface RetirementTimelineClient {
  void ensurePlanningTimeline(Long portfolioId);

  PastPlanningYear createHistoricalDraft(Long portfolioId, int year);

  PastPlanningYear seedHistoricalBaselineFromPlan(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions);

  List<Integer> prefillHistoricalYears(Long portfolioId, int planStartYear);

  PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year);

  YearReviewMode reviewMode(Long portfolioId, int year);

  RetirementTimelineContracts.ForwardTimelineResponse loadForwardTimeline(
      Long portfolioId,
      RetirementProjection projection,
      SimulationScenario scenario,
      CurrencyType displayCurrency);

  RetirementTimelineContracts.HistoricalYearReviewResponse displayYearReview(
      Long portfolioId, PastPlanningYear planningYear, CurrencyType displayCurrency);

  PastPlanningYear pastYear(Long portfolioId, int year);

  YearReview yearReview(PastPlanningYear year);

  boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric);

  PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year);

  void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions);

  void saveCurrentManualValue(
      Long portfolioId,
      int year,
      PlanningMetric metric,
      BigDecimal amount,
      CurrencyType displayCurrency,
      String note);

  void saveDraftManualValue(
      Long portfolioId,
      int year,
      PlanningMetric metric,
      BigDecimal amount,
      CurrencyType displayCurrency,
      String note);

  PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile);

  PastPlanningYear closeHistoricalDraft(Long portfolioId, int year);

  void reopenHistoricalYear(Long portfolioId, int year);

  HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear);

  void rebaseline(Long portfolioId, Long planId, PlanningBaseline baseline);
}
