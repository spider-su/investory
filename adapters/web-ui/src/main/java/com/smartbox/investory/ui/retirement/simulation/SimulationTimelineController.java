package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import com.smartbox.investory.ui.profile.ProfileClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Owns simulation timeline, year-review, and rebaseline routes. */
@Controller
public class SimulationTimelineController {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementTimelineClient timeline;
  private final RetirementPresentationClient presentation;
  private final RetirementProjectionClient projections;
  private final Clock clock;
  private final SimulationTimelinePageAssembler pageAssembler;

  @org.springframework.beans.factory.annotation.Autowired private PortfolioContextReader portfolios;

  public SimulationTimelineController(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementTimelineClient timeline,
      RetirementPresentationClient presentation,
      RetirementProjectionClient projections,
      Clock clock) {
    this.profiles = profiles;
    this.plans = plans;
    this.timeline = timeline;
    this.presentation = presentation;
    this.projections = projections;
    this.clock = clock;
    this.pageAssembler =
        new SimulationTimelinePageAssembler(profiles, plans, timeline, presentation, projections);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/past/{year}")
  public String createPastYear(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    if (planId == null) {
      timeline.createHistoricalDraft(portfolioId, year);
    } else {
      var plan = plans.details(portfolioId, planId);
      timeline.seedHistoricalBaselineFromPlan(
          portfolioId, year, planId, profiles.loadProfile(portfolioId), plan.assumptions());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/prefill")
  public String prefillHistoricalYears(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    int startYear =
        planId == null
            ? Year.now(clock).getValue()
            : plans.details(portfolioId, planId).assumptions().planStartYear();
    timeline.prefillHistoricalYears(portfolioId, startYear);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping({
    "/portfolios/{portfolioId}/simulation/timeline/past/{year}/refresh-derived",
    "/portfolios/{portfolioId}/simulation/timeline/past/{year}/refresh-accounting"
  })
  public String refreshPastDerivedValues(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    timeline.refreshHistoricalDerivedValues(portfolioId, year);
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @GetMapping("/portfolios/{portfolioId}/simulation/timeline/{year}")
  public String planningYearDetail(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    return pageAssembler.assemble(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario, model);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/baseline")
  public String setCurrentBaseline(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @RequestParam Long planId,
      @RequestParam int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    var profile = profiles.loadProfile(portfolioId);
    var plan = plans.details(portfolioId, planId);
    timeline.setCurrentBaseline(portfolioId, year, planId, profile, plan.assumptions());
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/plans/{planId}/rebaseline")
  public String rebaselinePlan(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable Long planId,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    PlanningBaseline baseline =
        PlanningBaseline.fromProfile(profiles.loadProfile(portfolioId), Year.now(clock).getValue());
    timeline.rebaseline(portfolioId, planId, baseline);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/current/{year}/manual")
  public String saveCurrentManual(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam PlanningMetric metric,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) String note,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    timeline.saveCurrentManualValue(
        portfolioId,
        year,
        metric,
        presentation.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
        note);
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/past/{year}/manual")
  public String savePastManual(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam PlanningMetric metric,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) String note,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    try {
      timeline.saveDraftManualValue(
          portfolioId,
          year,
          metric,
          presentation.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
          note);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/current/{year}/close")
  public String closeCurrentYear(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    timeline.closeCurrentYear(portfolioId, year, profiles.loadProfile(portfolioId));
    return SimulationRedirects.simulation(
        portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/past/{year}/close")
  public String closeHistoricalDraft(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    try {
      timeline.closeHistoricalDraft(portfolioId, year);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  @PostMapping("/portfolios/{portfolioId}/simulation/timeline/{year}/reopen")
  public String reopenPlanningYear(
      @org.springframework.web.bind.annotation.PathVariable Long portfolioId,
      @PathVariable int year,
      @RequestParam(required = false) CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    planningDisplayCurrency = resolveCurrency(portfolioId, planningDisplayCurrency);
    try {
      timeline.reopenHistoricalYear(portfolioId, year);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return SimulationRedirects.planningYear(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  private CurrencyType resolveCurrency(Long portfolioId, CurrencyType requested) {
    if (requested != null) return requested;
    if (portfolios == null) return CurrencyType.PLN;
    return portfolios
        .findById(portfolioId)
        .map(context -> context.localCurrency())
        .orElse(CurrencyType.PLN);
  }
}
