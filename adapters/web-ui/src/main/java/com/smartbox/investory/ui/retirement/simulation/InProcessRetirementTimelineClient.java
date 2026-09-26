package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.contract.RetirementTimelineContracts.*;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.rest.RetirementTimelineRestController;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** In-process adapter for retirement timeline operations. */
@Component
public class InProcessRetirementTimelineClient implements RetirementTimelineClient {
  private final RetirementTimelineRestController rest;

  public InProcessRetirementTimelineClient(RetirementTimelineRestController rest) {
    this.rest = rest;
  }

  public void ensurePlanningTimeline(Long portfolioId) {
    rest.ensurePlanningTimeline(portfolioId);
  }

  public PastPlanningYear createHistoricalDraft(Long portfolioId, int year) {
    return rest.createHistoricalDraft(portfolioId, year);
  }

  public PastPlanningYear seedHistoricalBaselineFromPlan(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    return rest.seedHistoricalBaselineFromPlan(
        portfolioId, year, new SeedHistoricalBaselineRequest(planId, profile, assumptions));
  }

  public List<Integer> prefillHistoricalYears(Long portfolioId, int planStartYear) {
    return rest.prefillHistoricalYears(portfolioId, planStartYear);
  }

  public PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year) {
    return rest.refreshHistoricalDerivedValues(portfolioId, year);
  }

  public YearReviewMode reviewMode(Long portfolioId, int year) {
    return rest.reviewMode(portfolioId, year);
  }

  public ForwardTimelineResponse loadForwardTimeline(
      Long portfolioId,
      RetirementProjection projection,
      SimulationScenario scenario,
      CurrencyType displayCurrency) {
    return rest.loadForwardTimeline(
        portfolioId, new ForwardTimelineRequest(projection, scenario, displayCurrency));
  }

  public HistoricalYearReviewResponse displayYearReview(
      Long portfolioId, PastPlanningYear planningYear, CurrencyType displayCurrency) {
    return rest.displayYearReview(
        new DisplayYearReviewRequest(portfolioId, planningYear, displayCurrency));
  }

  public PastPlanningYear pastYear(Long portfolioId, int year) {
    return rest.pastYear(portfolioId, year);
  }

  public YearReview yearReview(PastPlanningYear year) {
    return rest.yearReview(year);
  }

  public boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric) {
    return rest.isHistoricalMetricEditable(portfolioId, year, metric);
  }

  public PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year) {
    return rest.historicalCloseStatus(portfolioId, year);
  }

  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    rest.setCurrentBaseline(
        portfolioId, year, new CurrentBaselineRequest(planId, profile, assumptions));
  }

  public void saveCurrentManualValue(
      Long portfolioId,
      int year,
      PlanningMetric metric,
      BigDecimal amount,
      CurrencyType displayCurrency,
      String note) {
    rest.saveCurrentManualValue(
        portfolioId, year, metric, new ManualValueRequest(amount, displayCurrency, note));
  }

  public void saveDraftManualValue(
      Long portfolioId,
      int year,
      PlanningMetric metric,
      BigDecimal amount,
      CurrencyType displayCurrency,
      String note) {
    rest.saveDraftManualValue(
        portfolioId, year, metric, new ManualValueRequest(amount, displayCurrency, note));
  }

  public PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile) {
    return rest.closeCurrentYear(portfolioId, year, profile);
  }

  public PastPlanningYear closeHistoricalDraft(Long portfolioId, int year) {
    return rest.closeHistoricalDraft(portfolioId, year);
  }

  public void reopenHistoricalYear(Long portfolioId, int year) {
    rest.reopenHistoricalYear(portfolioId, year);
  }

  public HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear) {
    return rest.reconcile(portfolioId, planningYear);
  }

  public void rebaseline(Long portfolioId, Long planId, PlanningBaseline baseline) {
    rest.rebaseline(portfolioId, new RebaselineRequest(planId, baseline));
  }
}
