package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.model.*;
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

  PlanningTimeline loadForwardTimeline(
      Long portfolioId, RetirementProjection projection, SimulationScenario scenario);

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
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note);

  void saveDraftManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note);

  PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile);

  PastPlanningYear closeHistoricalDraft(Long portfolioId, int year);

  void reopenHistoricalYear(Long portfolioId, int year);

  HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear);

  void rebaseline(Long portfolioId, Long planId, PlanningBaseline baseline);
}
