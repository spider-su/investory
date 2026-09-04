package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementTimelineApi;
import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-process adapter for retirement timeline operations. */
@Component
public class InProcessRetirementTimelineClient implements RetirementTimelineClient {
  private final RetirementTimelineApi api;

  public InProcessRetirementTimelineClient(
      @Qualifier("retirementPlanningApplicationService") RetirementTimelineApi api) {
    this.api = api;
  }

  public void rollover(Long portfolioId) {
    api.rollover(portfolioId);
  }

  public PastPlanningYear createHistoricalDraft(Long portfolioId, int year) {
    return api.createHistoricalDraft(portfolioId, year);
  }

  public PastPlanningYear seedHistoricalBaselineFromPlan(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    return api.seedHistoricalBaselineFromPlan(
        portfolioId, year, planId, revisionId, profile, assumptions);
  }

  public List<Integer> prefillHistoricalYears(Long portfolioId, int planStartYear) {
    return api.prefillHistoricalYears(portfolioId, planStartYear);
  }

  public PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year) {
    return api.refreshHistoricalDerivedValues(portfolioId, year);
  }

  public YearReviewMode reviewMode(Long portfolioId, int year) {
    return api.reviewMode(portfolioId, year);
  }

  public PlanningTimeline loadForwardTimeline(
      Long portfolioId,
      InvestmentProfile profile,
      ForwardSimulationInput forward,
      SimulationScenario scenario) {
    return api.loadForwardTimeline(portfolioId, profile, forward, scenario);
  }

  public PastPlanningYear pastYear(Long portfolioId, int year) {
    return api.pastYear(portfolioId, year);
  }

  public YearReview yearReview(PastPlanningYear year) {
    return api.yearReview(year);
  }

  public boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric) {
    return api.isHistoricalMetricEditable(portfolioId, year, metric);
  }

  public PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year) {
    return api.historicalCloseStatus(portfolioId, year);
  }

  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    api.setCurrentBaseline(portfolioId, year, planId, revisionId, profile, assumptions);
  }

  public void saveCurrentManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note) {
    api.saveCurrentManualValue(portfolioId, year, metric, amount, note);
  }

  public void saveDraftManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note) {
    api.saveDraftManualValue(portfolioId, year, metric, amount, note);
  }

  public PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile) {
    return api.closeCurrentYear(portfolioId, year, profile);
  }

  public PastPlanningYear closeHistoricalDraft(Long portfolioId, int year) {
    return api.closeHistoricalDraft(portfolioId, year);
  }

  public void reopenHistoricalYear(Long portfolioId, int year) {
    api.reopenHistoricalYear(portfolioId, year);
  }

  public HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear) {
    return api.reconcile(portfolioId, planningYear);
  }

  public com.smartbox.investory.retirement.api.model.RevisionSummary rebaseline(
      Long portfolioId, Long planId, PlanningBaseline baseline) {
    return api.rebaseline(portfolioId, planId, baseline);
  }
}
