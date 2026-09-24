package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanInputApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.RetirementTimelineApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.rest.RetirementTimelineContracts.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

/** REST resource for retirement timeline and editor operations. */
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/retirement/timeline")
public class RetirementTimelineRestController {
  private final RetirementPlanInputApi planInput;
  private final RetirementPresentationApi presentation;
  private final RetirementTimelineApi timeline;

  public RetirementTimelineRestController(
      @Qualifier("retirementPlanningApplicationService") RetirementPlanInputApi planInput,
      @Qualifier("retirementPlanningApplicationService") RetirementPresentationApi presentation,
      @Qualifier("retirementPlanningApplicationService") RetirementTimelineApi timeline) {
    this.planInput = planInput;
    this.presentation = presentation;
    this.timeline = timeline;
  }

  @PostMapping("/editor/normalize")
  public NormalizedPlanInput normalizePlanEditorInput(
      @RequestBody NormalizePlanEditorInputRequest request) {
    return planInput.normalizePlanEditorInput(
        request.input(), request.base(), request.displayCurrency());
  }

  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return presentation.toDisplay(canonical, display);
  }

  public BigDecimal fromDisplay(BigDecimal amount, CurrencyType display, BigDecimal fallback) {
    return presentation.fromDisplay(amount, display, fallback);
  }

  public PastPlanningYear display(PastPlanningYear past, CurrencyType display) {
    return presentation.display(past, display);
  }

  public HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation value, CurrencyType display) {
    return presentation.displayReconciliation(value, display);
  }

  public PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display) {
    return presentation.displayProfile(profile, display);
  }

  public Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> values, CurrencyType display) {
    return presentation.displaySummaries(values, display);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline value, CurrencyType currency) {
    return presentation.displayTimelineMoney(value, currency);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline value, CurrencyType currency, SimulationAssumptions assumptions) {
    return presentation.displayTimelineMoney(value, currency, assumptions);
  }

  public PlanRiskView displayPlanRisks(SimulationSensitivityAnalysis value, CurrencyType display) {
    return presentation.displayPlanRisks(value, display);
  }

  public PlanningFlexibilityMoney displayPlanningFlexibility(
      SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return presentation.displayPlanningFlexibility(spending, retirement, display);
  }

  public SimulationChartData displayCharts(SimulationChartData value, CurrencyType display) {
    return presentation.displayCharts(value, display);
  }

  @PostMapping("/ensure")
  public void ensurePlanningTimeline(@PathVariable Long portfolioId) {
    timeline.ensurePlanningTimeline(portfolioId);
  }

  @PostMapping("/years/{year}/draft")
  public PastPlanningYear createHistoricalDraft(
      @PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.createHistoricalDraft(portfolioId, year);
  }

  @PostMapping("/years/{year}/draft/seed")
  public PastPlanningYear seedHistoricalBaselineFromPlan(
      @PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestBody SeedHistoricalBaselineRequest request) {
    return timeline.seedHistoricalBaselineFromPlan(
        portfolioId, year, request.planId(), request.profile(), request.assumptions());
  }

  @PostMapping("/prefill")
  public List<Integer> prefillHistoricalYears(
      @PathVariable Long portfolioId, @RequestParam int planStartYear) {
    return timeline.prefillHistoricalYears(portfolioId, planStartYear);
  }

  @PostMapping("/years/{year}/refresh")
  public PastPlanningYear refreshHistoricalDerivedValues(
      @PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.refreshHistoricalDerivedValues(portfolioId, year);
  }

  @GetMapping("/years/{year}/mode")
  public YearReviewMode reviewMode(@PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.reviewMode(portfolioId, year);
  }

  @PostMapping("/forward")
  public PlanningTimeline loadForwardTimeline(
      @PathVariable Long portfolioId, @RequestBody ForwardTimelineRequest request) {
    return timeline.loadForwardTimeline(portfolioId, request.projection(), request.scenario());
  }

  @GetMapping("/years/{year}")
  public PastPlanningYear pastYear(@PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.pastYear(portfolioId, year);
  }

  @PostMapping("/year-review")
  public YearReview yearReview(@RequestBody PastPlanningYear year) {
    return timeline.yearReview(year);
  }

  @GetMapping("/years/{year}/metrics/{metric}/editable")
  public boolean isHistoricalMetricEditable(
      @PathVariable Long portfolioId, @PathVariable int year, @PathVariable PlanningMetric metric) {
    return timeline.isHistoricalMetricEditable(portfolioId, year, metric);
  }

  @GetMapping("/years/{year}/close-status")
  public PlanningYearCloseStatus historicalCloseStatus(
      @PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.historicalCloseStatus(portfolioId, year);
  }

  @PostMapping("/years/{year}/baseline")
  public void setCurrentBaseline(
      @PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestBody CurrentBaselineRequest request) {
    timeline.setCurrentBaseline(
        portfolioId, year, request.planId(), request.profile(), request.assumptions());
  }

  @PutMapping("/years/{year}/manual-values/{metric}")
  public void saveCurrentManualValue(
      @PathVariable Long portfolioId,
      @PathVariable int year,
      @PathVariable PlanningMetric metric,
      @RequestBody ManualValueRequest request) {
    timeline.saveCurrentManualValue(portfolioId, year, metric, request.amount(), request.note());
  }

  @PutMapping("/years/{year}/draft/manual-values/{metric}")
  public void saveDraftManualValue(
      @PathVariable Long portfolioId,
      @PathVariable int year,
      @PathVariable PlanningMetric metric,
      @RequestBody ManualValueRequest request) {
    timeline.saveDraftManualValue(portfolioId, year, metric, request.amount(), request.note());
  }

  @PostMapping("/years/{year}/close")
  public PastPlanningYear closeCurrentYear(
      @PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestBody InvestmentProfile profile) {
    return timeline.closeCurrentYear(portfolioId, year, profile);
  }

  @PostMapping("/years/{year}/draft/close")
  public PastPlanningYear closeHistoricalDraft(
      @PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.closeHistoricalDraft(portfolioId, year);
  }

  @PostMapping("/years/{year}/reopen")
  public void reopenHistoricalYear(@PathVariable Long portfolioId, @PathVariable int year) {
    timeline.reopenHistoricalYear(portfolioId, year);
  }

  @PostMapping("/reconcile")
  public HistoricalReconciliation reconcile(
      @PathVariable Long portfolioId, @RequestBody PastPlanningYear planningYear) {
    return timeline.reconcile(portfolioId, planningYear);
  }

  @PostMapping("/rebaseline")
  public void rebaseline(@PathVariable Long portfolioId, @RequestBody RebaselineRequest request) {
    timeline.rebaseline(portfolioId, request.planId(), request.baseline());
  }
}
