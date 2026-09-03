package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

/** Assembles the live and historical planning-year review pages. */
@Component
final class SimulationTimelinePageAssembler {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementPlanningClient planning;
  private final RetirementProjectionClient projections;

  SimulationTimelinePageAssembler(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementPlanningClient planning,
      RetirementProjectionClient projections) {
    this.profiles = profiles;
    this.plans = plans;
    this.planning = planning;
    this.projections = projections;
  }

  String assemble(
      Long portfolioId,
      int year,
      CurrencyType currency,
      Long planId,
      SimulationScenario scenario,
      Model model) {
    var profile = profiles.loadProfile(portfolioId);
    model.addAttribute("profile", profile);
    model.addAttribute("planningDisplayCurrency", currency);
    model.addAttribute("planningPresentation", planning);
    model.addAttribute("selectedPlanId", planId);
    model.addAttribute("selectedScenario", scenario);
    YearReviewMode mode = planning.reviewMode(portfolioId, year);
    if (mode == YearReviewMode.LIVE) {
      var projection = projections.load(portfolioId, planId);
      var timeline =
          planning.loadForwardTimeline(portfolioId, profile, projection.forward(), scenario);
      var row =
          timeline.years().stream()
              .filter(r -> r.state() == PlanningTimelineState.LIVE && r.year() == year)
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Live planning year is unavailable"));
      var money =
          planning
              .displayTimelineMoney(timeline, currency, projection.projectedAssumptions())
              .get(year);
      model.addAttribute(
          "liveReview",
          new LiveYearReviewView(
              year, row.age(), currency, row.current(), money, projection.projectedAssumptions()));
      return "live-year-review";
    }
    if (mode == YearReviewMode.NONE)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Projected year has no review");
    var stored = planning.pastYear(portfolioId, year);
    model.addAttribute("planningYear", planning.display(stored, currency));
    model.addAttribute(
        "baselineRevision",
        stored.baselineRevisionId() == null || stored.baselinePlanId() == null
            ? null
            : plans.details(portfolioId, stored.baselinePlanId()).currentRevision());
    var reconciliation = planning.reconcile(portfolioId, stored);
    model.addAttribute(
        "planningReconciliation", planning.displayReconciliation(reconciliation, currency));
    model.addAttribute("yearReview", planning.yearReview(stored));
    Set<PlanningMetric> editable = EnumSet.noneOf(PlanningMetric.class);
    stored
        .values()
        .keySet()
        .forEach(
            metric -> {
              if (planning.isHistoricalMetricEditable(portfolioId, year, metric))
                editable.add(metric);
            });
    model.addAttribute("editableMetrics", editable);
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
}
