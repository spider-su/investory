package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanInputApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.RetirementTimelineApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** In-process adapter from MVC to Retirement's public planning API. */
@Component
public class InProcessRetirementPlanningClient implements RetirementPlanningClient {
  private final RetirementPlanInputApi retirementPlanInputApi;
  private final RetirementTimelineApi retirementTimelineApi;
  private final RetirementPresentationApi retirementPresentationApi;

  public InProcessRetirementPlanningClient(
      @Qualifier("retirementPlanningApplicationService")
          RetirementPlanInputApi retirementPlanInputApi,
      @Qualifier("retirementPlanningApplicationService")
          RetirementTimelineApi retirementTimelineApi,
      @Qualifier("retirementPlanningApplicationService")
          RetirementPresentationApi retirementPresentationApi) {
    this.retirementPlanInputApi = retirementPlanInputApi;
    this.retirementTimelineApi = retirementTimelineApi;
    this.retirementPresentationApi = retirementPresentationApi;
  }

  @Override
  public void rollover(Long portfolioId) {
    retirementTimelineApi.rollover(portfolioId);
  }

  @Override
  public NormalizedPlanInput normalizePlanEditorInput(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency) {
    return retirementPlanInputApi.normalizePlanEditorInput(input, base, displayCurrency);
  }

  @Override
  public PastPlanningYear createHistoricalDraft(Long portfolioId, int year) {
    return retirementTimelineApi.createHistoricalDraft(portfolioId, year);
  }

  @Override
  public PastPlanningYear seedHistoricalBaselineFromPlan(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    return retirementTimelineApi.seedHistoricalBaselineFromPlan(
        portfolioId, year, planId, revisionId, profile, assumptions);
  }

  @Override
  public List<Integer> prefillHistoricalYears(Long portfolioId, int planStartYear) {
    return retirementTimelineApi.prefillHistoricalYears(portfolioId, planStartYear);
  }

  @Override
  public PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year) {
    return retirementTimelineApi.refreshHistoricalDerivedValues(portfolioId, year);
  }

  @Override
  public YearReviewMode reviewMode(Long portfolioId, int year) {
    return retirementTimelineApi.reviewMode(portfolioId, year);
  }

  @Override
  public PlanningTimeline loadForwardTimeline(
      Long portfolioId,
      InvestmentProfile profile,
      ForwardSimulationInput forward,
      SimulationScenario scenario) {
    return retirementTimelineApi.loadForwardTimeline(portfolioId, profile, forward, scenario);
  }

  @Override
  public PastPlanningYear pastYear(Long portfolioId, int year) {
    return retirementTimelineApi.pastYear(portfolioId, year);
  }

  @Override
  public YearReview yearReview(PastPlanningYear year) {
    return retirementTimelineApi.yearReview(year);
  }

  @Override
  public boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric) {
    return retirementTimelineApi.isHistoricalMetricEditable(portfolioId, year, metric);
  }

  @Override
  public PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year) {
    return retirementTimelineApi.historicalCloseStatus(portfolioId, year);
  }

  @Override
  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    retirementTimelineApi.setCurrentBaseline(
        portfolioId, year, planId, revisionId, profile, assumptions);
  }

  @Override
  public void saveCurrentManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note) {
    retirementTimelineApi.saveCurrentManualValue(portfolioId, year, metric, amount, note);
  }

  @Override
  public void saveDraftManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note) {
    retirementTimelineApi.saveDraftManualValue(portfolioId, year, metric, amount, note);
  }

  @Override
  public PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile) {
    return retirementTimelineApi.closeCurrentYear(portfolioId, year, profile);
  }

  @Override
  public PastPlanningYear closeHistoricalDraft(Long portfolioId, int year) {
    return retirementTimelineApi.closeHistoricalDraft(portfolioId, year);
  }

  @Override
  public void reopenHistoricalYear(Long portfolioId, int year) {
    retirementTimelineApi.reopenHistoricalYear(portfolioId, year);
  }

  @Override
  public HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear) {
    return retirementTimelineApi.reconcile(portfolioId, planningYear);
  }

  @Override
  public com.smartbox.investory.retirement.api.model.RevisionSummary rebaseline(
      Long portfolioId, Long planId, PlanningBaseline baseline) {
    return retirementTimelineApi.rebaseline(portfolioId, planId, baseline);
  }

  @Override
  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return retirementPresentationApi.toDisplay(canonical, display);
  }

  @Override
  public BigDecimal fromDisplay(
      BigDecimal amount, CurrencyType display, BigDecimal fallbackCanonicalAmount) {
    return retirementPresentationApi.fromDisplay(amount, display, fallbackCanonicalAmount);
  }

  @Override
  public PastPlanningYear display(PastPlanningYear past, CurrencyType display) {
    return retirementPresentationApi.display(past, display);
  }

  @Override
  public HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation reconciliation, CurrencyType display) {
    return retirementPresentationApi.displayReconciliation(reconciliation, display);
  }

  @Override
  public PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display) {
    return retirementPresentationApi.displayProfile(profile, display);
  }

  @Override
  public Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, CurrencyType display) {
    return retirementPresentationApi.displaySummaries(summaries, display);
  }

  @Override
  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency) {
    return retirementPresentationApi.displayTimelineMoney(timeline, currency);
  }

  @Override
  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency, SimulationAssumptions assumptions) {
    return retirementPresentationApi.displayTimelineMoney(timeline, currency, assumptions);
  }

  @Override
  public PlanRiskView displayPlanRisks(
      SimulationSensitivityAnalysis analysis, CurrencyType display) {
    return retirementPresentationApi.displayPlanRisks(analysis, display);
  }

  @Override
  public PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return retirementPresentationApi.displayPlanningFlexibility(spending, retirement, display);
  }

  @Override
  public SimulationChartData displayCharts(SimulationChartData charts, CurrencyType display) {
    return retirementPresentationApi.displayCharts(charts, display);
  }
}
