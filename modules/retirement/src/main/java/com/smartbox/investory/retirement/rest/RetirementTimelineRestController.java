package com.smartbox.investory.retirement.rest;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanInputApi;
import com.smartbox.investory.retirement.api.RetirementPresentationApi;
import com.smartbox.investory.retirement.api.RetirementTimelineApi;
import com.smartbox.investory.retirement.api.contract.RetirementTimelineContracts.*;
import com.smartbox.investory.retirement.api.model.*;
import java.util.List;
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
  public ForwardTimelineResponse loadForwardTimeline(
      @PathVariable Long portfolioId, @RequestBody ForwardTimelineRequest request) {
    PlanningTimeline value =
        timeline.loadForwardTimeline(portfolioId, request.projection(), request.scenario());
    return new ForwardTimelineResponse(
        value,
        presentation.displayTimelineMoney(
            value, request.displayCurrency(), request.projection().projectedAssumptions()));
  }

  @GetMapping("/years/{year}")
  public PastPlanningYear pastYear(@PathVariable Long portfolioId, @PathVariable int year) {
    return timeline.pastYear(portfolioId, year);
  }

  @PostMapping("/year-review")
  public YearReview yearReview(@RequestBody PastPlanningYear year) {
    return timeline.yearReview(year);
  }

  @PostMapping("/year-review/display")
  public HistoricalYearReviewResponse displayYearReview(
      @RequestBody DisplayYearReviewRequest request) {
    YearReview yearReview = timeline.yearReview(request.planningYear());
    return new HistoricalYearReviewResponse(
        presentation.display(request.planningYear(), request.displayCurrency()),
        presentation.displayReconciliation(
            timeline.reconcile(request.portfolioId(), request.planningYear()),
            request.displayCurrency()),
        yearReview,
        new DisplayYearReview(
            presentation.toDisplay(yearReview.progress().difference(), request.displayCurrency()),
            yearReview.drivers().stream()
                .map(
                    driver ->
                        new YearReview.YearReviewDriver(
                            driver.label(),
                            presentation.toDisplay(driver.impact(), request.displayCurrency())))
                .toList(),
            presentation.toDisplay(yearReview.otherChanges(), request.displayCurrency())));
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
    timeline.saveCurrentManualValue(
        portfolioId,
        year,
        metric,
        presentation.fromDisplay(
            request.amount(), request.displayCurrency(), java.math.BigDecimal.ZERO),
        request.note());
  }

  @PutMapping("/years/{year}/draft/manual-values/{metric}")
  public void saveDraftManualValue(
      @PathVariable Long portfolioId,
      @PathVariable int year,
      @PathVariable PlanningMetric metric,
      @RequestBody ManualValueRequest request) {
    timeline.saveDraftManualValue(
        portfolioId,
        year,
        metric,
        presentation.fromDisplay(
            request.amount(), request.displayCurrency(), java.math.BigDecimal.ZERO),
        request.note());
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
