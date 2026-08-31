package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningMetricValue;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.PlanningTimelineYear;
import com.smartbox.investory.retirement.api.model.SimulationCustomDeltas;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Owns simulation timeline, year-review, rollover, and rebaseline routes. */
@Controller
public class SimulationTimelineController {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementPlanningClient planning;
  private final RetirementProjectionClient projections;
  private final Clock clock;

  public SimulationTimelineController(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementPlanningClient planning,
      RetirementProjectionClient projections,
      Clock clock) {
    this.profiles = profiles;
    this.plans = plans;
    this.planning = planning;
    this.projections = projections;
    this.clock = clock;
  }

  @PostMapping("/simulation/rollover")
  public String rollover(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planning.rollover(portfolioId);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/timeline/past/{year}")
  public String createPastYear(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    if (planId == null) {
      planning.createHistoricalDraft(portfolioId, year);
    } else {
      var plan = plans.details(portfolioId, planId);
      planning.seedHistoricalBaselineFromPlan(
          portfolioId,
          year,
          planId,
          plan.currentRevisionId(),
          profiles.loadProfile(portfolioId),
          plan.assumptions());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @PostMapping("/simulation/timeline/prefill")
  public String prefillHistoricalYears(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    int startYear =
        planId == null
            ? Year.now(clock).getValue()
            : plans.details(portfolioId, planId).assumptions().planStartYear();
    planning.prefillHistoricalYears(portfolioId, startYear);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping({
    "/simulation/timeline/past/{year}/refresh-derived",
    "/simulation/timeline/past/{year}/refresh-accounting"
  })
  public String refreshPastDerivedValues(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planning.refreshHistoricalDerivedValues(portfolioId, year);
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @GetMapping("/simulation/timeline/{year}")
  public String planningYearDetail(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    var profile = profiles.loadProfile(portfolioId);
    model.addAttribute("profile", profile);
    model.addAttribute("planningDisplayCurrency", planningDisplayCurrency);
    model.addAttribute("planningPresentation", planning);
    model.addAttribute("selectedPlanId", planId);
    model.addAttribute("selectedScenario", selectedScenario);
    YearReviewMode reviewMode = planning.reviewMode(portfolioId, year);
    if (reviewMode == YearReviewMode.LIVE) {
      var projection = projections.load(portfolioId, planId);
      PlanningTimeline timeline =
          planning.loadForwardTimeline(
              portfolioId,
              profile,
              projection.forward(),
              selectedScenario,
              SimulationCustomDeltas.zero());
      PlanningTimelineYear liveRow =
          timeline.years().stream()
              .filter(row -> row.state() == PlanningTimelineState.LIVE && row.year() == year)
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Live planning year is unavailable"));
      var money =
          planning
              .displayTimelineMoney(
                  timeline, planningDisplayCurrency, projection.projectedAssumptions())
              .get(year);
      model.addAttribute(
          "liveReview",
          new LiveYearReviewView(
              year,
              liveRow.age(),
              planningDisplayCurrency,
              liveRow.current(),
              money,
              projection.projectedAssumptions()));
      return "live-year-review";
    }
    if (reviewMode == YearReviewMode.NONE)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND, "Projected year has no review");
    var stored = planning.pastYear(portfolioId, year);
    model.addAttribute("planningYear", planning.display(stored, planningDisplayCurrency));
    model.addAttribute(
        "baselineRevision",
        stored.baselineRevisionId() == null || stored.baselinePlanId() == null
            ? null
            : plans.details(portfolioId, stored.baselinePlanId()).currentRevision());
    HistoricalReconciliation historicalReconciliation = planning.reconcile(portfolioId, stored);
    model.addAttribute(
        "planningReconciliation",
        planning.displayReconciliation(historicalReconciliation, planningDisplayCurrency));
    model.addAttribute("yearReview", planning.yearReview(stored));
    Set<PlanningMetric> editableMetrics = EnumSet.noneOf(PlanningMetric.class);
    stored
        .values()
        .keySet()
        .forEach(
            metric -> {
              if (planning.isHistoricalMetricEditable(portfolioId, year, metric))
                editableMetrics.add(metric);
            });
    model.addAttribute("editableMetrics", editableMetrics);
    model.addAttribute("planningCloseStatus", planning.historicalCloseStatus(portfolioId, year));
    model.addAttribute(
        "reviewMetrics",
        java.util.List.of(
            PlanningMetric.NET_WORTH,
            PlanningMetric.MARKET_ASSETS,
            PlanningMetric.RENTAL_INCOME,
            PlanningMetric.BOND_INCOME,
            PlanningMetric.CORE_SPENDING,
            PlanningMetric.DISCRETIONARY_SPENDING,
            PlanningMetric.MARKET_RETURN,
            PlanningMetric.MARKET_WITHDRAWAL));
    PlanningMetricValue netWorth = stored.values().get(PlanningMetric.NET_WORTH);
    PlanningMetricValue marketAssets = stored.values().get(PlanningMetric.MARKET_ASSETS);
    model.addAttribute(
        "netWorthUnavailableWithMarketAssets",
        (netWorth == null || !netWorth.available())
            && marketAssets != null
            && marketAssets.available());
    return "planning-year";
  }

  @PostMapping("/simulation/timeline/baseline")
  public String setCurrentBaseline(
      @RequestParam Long portfolioId,
      @RequestParam Long planId,
      @RequestParam int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    var profile = profiles.loadProfile(portfolioId);
    var plan = plans.details(portfolioId, planId);
    planning.setCurrentBaseline(
        portfolioId, year, planId, plan.currentRevisionId(), profile, plan.assumptions());
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{planId}/rebaseline")
  public String rebaselinePlan(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    PlanningBaseline baseline =
        PlanningBaseline.fromProfile(profiles.loadProfile(portfolioId), Year.now(clock).getValue());
    planning.rebaseline(portfolioId, planId, baseline);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/timeline/current/{year}/manual")
  public String saveCurrentManual(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam PlanningMetric metric,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) String note,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planning.saveCurrentManualValue(
        portfolioId,
        year,
        metric,
        planning.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
        note);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/timeline/past/{year}/manual")
  public String savePastManual(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam PlanningMetric metric,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) String note,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    try {
      planning.saveDraftManualValue(
          portfolioId,
          year,
          metric,
          planning.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
          note);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @PostMapping("/simulation/timeline/current/{year}/close")
  public String closeCurrentYear(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planning.closeCurrentYear(portfolioId, year, profiles.loadProfile(portfolioId));
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/timeline/past/{year}/close")
  public String closeHistoricalDraft(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    try {
      planning.closeHistoricalDraft(portfolioId, year);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @PostMapping("/simulation/timeline/{year}/reopen")
  public String reopenPlanningYear(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    try {
      planning.reopenHistoricalYear(portfolioId, year);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }
}
