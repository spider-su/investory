package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanInputApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.RetirementTimelineApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.NormalizedPlanInput;
import com.smartbox.investory.retirement.api.model.PlanEditorInput;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Application facade keeping web adapters behind the public retirement planning contract. */
@Service("retirementPlanningApplicationService")
@RequiredArgsConstructor
public class RetirementPlanningApplicationService
    implements RetirementPlanInputApi, RetirementTimelineApi, RetirementPresentationApi {
  private final PlanningTimelineFacade timeline;
  private final PlanningCurrencyPresentationService presentation;
  private final AnnualPlanningRolloverService rollover;
  private final PlanningReconciliationService reconciliation;
  private final RetirementPlanReviewService planReviews;
  private final PlanEditorInputNormalizer editorInputNormalizer;

  @Override
  public void rollover(Long portfolioId) {
    rollover.rollover(portfolioId);
  }

  @Override
  public NormalizedPlanInput normalizePlanEditorInput(
      PlanEditorInput input, SimulationAssumptions base, CurrencyType displayCurrency) {
    PlanEditorInputNormalizer.Normalized normalized =
        editorInputNormalizer.normalize(input, base, displayCurrency);
    return new NormalizedPlanInput(normalized.assumptions(), normalized.warnings());
  }

  @Override
  public PastPlanningYear createHistoricalDraft(Long portfolioId, int year) {
    return timeline.createHistoricalDraft(portfolioId, year);
  }

  @Override
  public PastPlanningYear seedHistoricalBaselineFromPlan(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    return timeline.seedHistoricalBaselineFromPlan(
        portfolioId, year, planId, revisionId, profile, assumptions);
  }

  @Override
  public List<Integer> prefillHistoricalYears(Long portfolioId, int planStartYear) {
    return timeline.prefillHistoricalYears(portfolioId, planStartYear);
  }

  @Override
  public PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year) {
    return timeline.refreshHistoricalDerivedValues(portfolioId, year);
  }

  @Override
  public YearReviewMode reviewMode(Long portfolioId, int year) {
    return timeline.reviewMode(portfolioId, year);
  }

  @Override
  public PlanningTimeline loadForwardTimeline(
      Long portfolioId,
      InvestmentProfile profile,
      ForwardSimulationInput forward,
      SimulationScenario scenario,
      SimulationCustomDeltas customDeltas) {
    return timeline.loadForwardTimeline(portfolioId, profile, forward, scenario, customDeltas);
  }

  @Override
  public PastPlanningYear pastYear(Long portfolioId, int year) {
    return timeline.pastYear(portfolioId, year);
  }

  @Override
  public YearReview yearReview(PastPlanningYear year) {
    return timeline.yearReview(year);
  }

  @Override
  public boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric) {
    return timeline.isHistoricalMetricEditable(portfolioId, year, metric);
  }

  @Override
  public PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year) {
    return timeline.historicalCloseStatus(portfolioId, year);
  }

  @Override
  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    timeline.setCurrentBaseline(portfolioId, year, planId, revisionId, profile, assumptions);
  }

  @Override
  public void saveCurrentManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note) {
    timeline.saveCurrentManualValue(portfolioId, year, metric, amount, note);
  }

  @Override
  public void saveDraftManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal amount, String note) {
    timeline.saveDraftManualValue(portfolioId, year, metric, amount, note);
  }

  @Override
  public PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile) {
    return timeline.closeCurrentYear(portfolioId, year, profile);
  }

  @Override
  public PastPlanningYear closeHistoricalDraft(Long portfolioId, int year) {
    return timeline.closeHistoricalDraft(portfolioId, year);
  }

  @Override
  public void reopenHistoricalYear(Long portfolioId, int year) {
    timeline.reopenHistoricalYear(portfolioId, year);
  }

  @Override
  public HistoricalReconciliation reconcile(Long portfolioId, PastPlanningYear planningYear) {
    return reconciliation.reconcile(portfolioId, planningYear);
  }

  @Override
  public com.smartbox.investory.retirement.api.model.RevisionSummary rebaseline(
      Long portfolioId, Long planId, PlanningBaseline baseline) {
    return planReviews.rebaseline(portfolioId, planId, baseline);
  }

  @Override
  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return presentation.toDisplay(canonical, display);
  }

  @Override
  public BigDecimal fromDisplay(
      BigDecimal amount, CurrencyType display, BigDecimal fallbackCanonicalAmount) {
    return presentation.fromDisplay(amount, display, fallbackCanonicalAmount);
  }

  @Override
  public PastPlanningYear display(PastPlanningYear past, CurrencyType display) {
    return presentation.display(past, display);
  }

  @Override
  public HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation value, CurrencyType display) {
    return presentation.displayReconciliation(value, display);
  }

  @Override
  public PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display) {
    return presentation.displayProfile(profile, display);
  }

  @Override
  public Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, CurrencyType display) {
    return presentation.displaySummaries(summaries, display);
  }

  @Override
  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline value, CurrencyType currency) {
    return presentation.displayTimelineMoney(value, currency);
  }

  @Override
  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline value, CurrencyType currency, SimulationAssumptions assumptions) {
    return presentation.displayTimelineMoney(value, currency, assumptions);
  }

  @Override
  public PlanRiskView displayPlanRisks(
      SimulationSensitivityAnalysis analysis, CurrencyType display) {
    return presentation.displayPlanRisks(analysis, display);
  }

  @Override
  public PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return presentation.displayPlanningFlexibility(spending, retirement, display);
  }

  @Override
  public SimulationChartData displayCharts(SimulationChartData charts, CurrencyType display) {
    return presentation.displayCharts(charts, display);
  }
}
