package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.PlanningBuckets;
import com.smartbox.investory.retirement.api.model.PlanningPresentation;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.ScenarioEffectiveAssumptions;
import com.smartbox.investory.retirement.api.model.SimulationEventType;
import com.smartbox.investory.retirement.api.model.SimulationScenarioSettings;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.profile.ProfileClient;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class RetirementSimulationController {
  private final ProfileClient profiles;
  private final RetirementPlanClient plans;
  private final RetirementTimelineClient planningTimeline;
  private final RetirementPresentationClient presentation;
  private final RetirementPlanInputClient planInput;
  private final RetirementProjectionClient projections;
  private final Clock clock;
  private final SimulationValueFormatter simulationValueFormatter;
  private final SimulationRequestMapper requestMapper;

  private final RetirementPreviewClient planEditorPreview;
  private final ScenarioObservationService scenarioObservations;
  private final SimulationCommandService commands;

  @Value("${develop.mode:false}")
  private boolean developMode;

  @Autowired
  public RetirementSimulationController(
      ProfileClient profiles,
      RetirementPlanClient plans,
      RetirementTimelineClient planningTimeline,
      RetirementPresentationClient presentation,
      RetirementPlanInputClient planInput,
      RetirementProjectionClient projections,
      Clock clock,
      RetirementPreviewClient planEditorPreview,
      ScenarioObservationService scenarioObservations,
      SimulationCommandService commands) {
    this.profiles = profiles;
    this.plans = plans;
    this.planningTimeline = planningTimeline;
    this.presentation = presentation;
    this.planInput = planInput;
    this.projections = projections;
    this.clock = clock;
    this.simulationValueFormatter = new SimulationValueFormatter(presentation);
    this.requestMapper = new SimulationRequestMapper(presentation, planInput, clock);
    this.planEditorPreview = planEditorPreview;
    this.scenarioObservations = scenarioObservations;
    this.commands = commands;
  }

  @GetMapping("/simulation")
  public String simulation(@ModelAttribute SimulationQuery query, Model model) {
    // Optional assumption parameters are retained as transient/query overrides for legacy deep
    // links. Saved plans remain the canonical source when an override is not supplied.
    int currentYear = Year.now(clock).getValue();
    int requestedCurrentAge = query.getCurrentAge() == null ? 40 : query.getCurrentAge();
    int requestedEndAge = query.getEndAge() == null ? 95 : query.getEndAge();
    Long portfolioId = query.getPortfolioId();
    CurrencyType planningDisplayCurrency = query.getPlanningDisplayCurrency();
    SimulationScenario selectedScenario = query.getSelectedScenario();
    Long selectedPlanId = plans.resolvePlanId(portfolioId, query.getPlanId()).orElse(null);
    com.smartbox.investory.retirement.api.model.PlanDetails selectedPlan =
        selectedPlanId == null ? null : plans.details(portfolioId, selectedPlanId);
    var projectionInput =
        projections.load(
            portfolioId,
            selectedPlanId,
            requestedCurrentAge,
            requestedEndAge,
            SimulationCustomDeltas.zero());
    var profile = projectionInput.profile();
    var base = projectionInput.assumptions();
    var assumptions = requestMapper.applyLegacyOverrides(base, query.legacyOverrides());
    CustomScenarioInput customInput = query.customScenarioInput();
    SimulationCustomDeltas customDeltas =
        selectedScenario == SimulationScenario.CUSTOM && !customInput.errors().isEmpty()
            ? SimulationCustomDeltas.zero()
            : selectedScenario == SimulationScenario.CUSTOM
                ? customInput.deltas()
                : SimulationCustomDeltas.zero();
    if (selectedScenario == SimulationScenario.CUSTOM && customInput.errors().isEmpty()) {
      try {
        SimulationScenarioSettings.forScenario(
            SimulationScenario.CUSTOM, assumptions, customDeltas);
      } catch (IllegalArgumentException ex) {
        customInput =
            customInput.withError("effective", "Effective assumption is outside the valid range.");
        customDeltas = SimulationCustomDeltas.zero();
      }
    }
    var baseline = selectedPlan == null ? null : selectedPlan.baseline();
    var projection = projections.project(profile, assumptions, baseline, customDeltas);
    var projectedAssumptions = projection.projectedAssumptions();
    var summaries = projection.summaries();
    PlanningTimeline timeline =
        planningTimeline.loadForwardTimeline(
            portfolioId, profile, projection.forward(), selectedScenario, customDeltas);
    boolean currentYearCloseAllowed =
        timeline.years().stream()
            .filter(row -> row.state() == PlanningTimelineState.LIVE)
            .anyMatch(row -> row.year() < currentYear);
    var startingPosition = presentation.displayProfile(profile, planningDisplayCurrency);
    BigDecimal displayAnnualExpenses =
        displayMoney(assumptions.annualLivingExpenses(), planningDisplayCurrency);
    BigDecimal displayAnnualCosts =
        displayMoney(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            planningDisplayCurrency);
    BigDecimal displayDiscretionaryExpenses =
        displayMoney(assumptions.annualDiscretionaryExpenses(), planningDisplayCurrency);
    BigDecimal displayAnnualPension =
        displayMoney(assumptions.annualPension(), planningDisplayCurrency);
    String activePlanName = selectedPlanId == null ? "Current assumptions" : selectedPlan.name();
    String activePlanSummary =
        projectedAssumptions.currentAge()
            + "–"
            + projectedAssumptions.endAge()
            + " · Retire at "
            + projectedAssumptions.retirementAge()
            + " · Effective cost growth "
            + PlanningPresentation.percentage(projectedAssumptions.effectiveSpendingGrowthRate());
    var displaySummaries =
        new LinkedHashMap<>(presentation.displaySummaries(summaries, planningDisplayCurrency));
    var timelineMoney =
        presentation.displayTimelineMoney(timeline, planningDisplayCurrency, projectedAssumptions);
    var yearlySummaries = RetirementYearSummaryView.from(timeline, timelineMoney);
    var toDisplayMoney =
        (java.util.function.Function<BigDecimal, BigDecimal>)
            amount -> displayMoney(amount, planningDisplayCurrency);
    var cashFlow =
        CashFlowSectionView.from(timeline, timelineMoney, projectedAssumptions, toDisplayMoney);
    var chartData =
        RetirementSimulationChartView.from(timeline, timelineMoney, projectedAssumptions);
    var planTimeline =
        PlanTimelineView.from(
            timeline,
            yearlySummaries,
            timelineMoney,
            projectedAssumptions,
            chartData.retirementYear(),
            chartData.pensionStartYear(),
            currentYear,
            toDisplayMoney);
    var scenarioAssumptions =
        ScenarioEffectiveAssumptions.forScenario(
            projection.projectedProfile(),
            projectedAssumptions,
            selectedScenario,
            projection.forward().context().asOfYear(),
            customDeltas);
    Map<String, ScenarioObservation> observations =
        scenarioObservations.load(portfolioId, timeline);
    var scenarioAssumptionRows =
        List.of(
            assumption(
                "Inflation",
                projectedAssumptions.inflationRate(),
                scenarioAssumptions.inflationRate(),
                false,
                observations),
            assumption(
                "Rental growth",
                projectedAssumptions.effectiveRentalIncomeGrowthRate(),
                scenarioAssumptions.rentalIncomeGrowthRate(),
                true,
                observations),
            assumption(
                "Bond return",
                scenarioAssumptions.planBondReturnRate(),
                scenarioAssumptions.bondReturnRate(),
                true,
                observations),
            assumption(
                "Equity return",
                projectedAssumptions.equityReturnRate(),
                scenarioAssumptions.equityReturnRate(),
                true,
                observations),
            assumption(
                "Spending growth",
                projectedAssumptions.effectiveSpendingGrowthRate(),
                scenarioAssumptions.spendingGrowthRate(),
                false,
                observations));
    model.addAttribute(
        "simulationPage",
        SimulationPageModelFactory.create(
            profile,
            startingPosition,
            assumptions,
            projectedAssumptions,
            planningDisplayCurrency,
            selectedPlanId,
            activePlanName,
            activePlanSummary,
            selectedScenario,
            scenarioAssumptions,
            scenarioAssumptionRows,
            CustomScenarioView.from(customInput),
            displaySummaries.get(selectedScenario),
            displayAnnualCosts,
            displayAnnualExpenses,
            displayDiscretionaryExpenses,
            displayAnnualPension,
            timeline,
            timelineMoney,
            yearlySummaries,
            planTimeline,
            cashFlow,
            currentYearCloseAllowed,
            chartData));
    return "simulation";
  }

  private BigDecimal displayMoney(BigDecimal amount, CurrencyType currency) {
    return simulationValueFormatter.money(amount, currency);
  }

  private ScenarioAssumptionView assumption(
      String name,
      BigDecimal plan,
      BigDecimal effective,
      boolean higherIsBetter,
      Map<String, ScenarioObservation> observations) {
    ScenarioObservation observation =
        observations.getOrDefault(name, ScenarioObservation.unavailable());
    return ScenarioAssumptionView.of(
        name,
        plan,
        effective,
        higherIsBetter,
        observation.value(),
        observation.label(),
        observation.period(),
        observation.availability());
  }

  @GetMapping("/simulation/plan/edit")
  public String editPlan(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    var profile = profiles.loadProfile(portfolioId);
    int currentYear = Year.now(clock).getValue();
    Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    SimulationAssumptions assumptions;
    String planName = "";
    if (selectedPlanId == null) {
      assumptions = SimulationAssumptions.defaults(profile, 40, 95, currentYear);
    } else {
      var selectedPlan = plans.details(portfolioId, selectedPlanId);
      assumptions = selectedPlan.assumptions();
      planName = selectedPlan.name();
    }
    model.addAttribute("profile", profile);
    model.addAttribute(
        "displayProfile", presentation.displayProfile(profile, planningDisplayCurrency));
    model.addAttribute("assumptions", assumptions);
    model.addAttribute(
        "planningBuckets",
        PlanningBuckets.fromProfileWithBondYield(
            profile,
            assumptions.equityReturnRate(),
            PlanningBuckets.baseBondYield(
                profile, assumptions.fixedIncomeReturnRate(), assumptions.startYear())));
    model.addAttribute("planStartYear", assumptions.planStartYear());
    model.addAttribute("ageAtPlanStart", assumptions.ageAtPlanStart());
    model.addAttribute(
        "currentPlanningAge",
        assumptions.ageAtPlanStart() + currentYear - assumptions.planStartYear());
    model.addAttribute("currentPlanningYear", currentYear);
    model.addAttribute("plannedRetirementYear", assumptions.retirementYear());
    model.addAttribute("planName", planName);
    model.addAttribute("selectedPlanId", selectedPlanId);
    model.addAttribute("selectedScenario", selectedScenario);
    model.addAttribute("planningDisplayCurrency", planningDisplayCurrency);
    model.addAttribute("developMode", developMode);
    var preview = planEditorPreview.preview(profile, assumptions, planningDisplayCurrency);
    model.addAttribute("currentRentalIncome", preview.rentalIncome());
    model.addAttribute("currentBondIncome", preview.bondIncome());
    model.addAttribute("plannedIncomeReferenceYear", preview.plannedIncomeReferenceYear());
    model.addAttribute("plannedRentalIncome", preview.plannedRentalIncome());
    model.addAttribute("plannedBondIncome", preview.plannedBondIncome());
    model.addAttribute(
        "plannedEmploymentIncome",
        preview.firstProjectedYear() == null
            ? null
            : preview.firstProjectedYear().employmentIncome());
    model.addAttribute("plannedInvestmentProfit", preview.plannedInvestmentProfit());
    model.addAttribute("plannedCapitalizedBondReturn", preview.plannedCapitalizedBondReturn());
    model.addAttribute("plannedPension", preview.plannedPension());
    model.addAttribute("plannedAnnualIncome", preview.plannedAnnualIncome());
    if (developMode) model.addAttribute("planPreview", preview);
    model.addAttribute("plans", plans.listPlans(portfolioId));
    model.addAttribute(
        "displayMonthlyLivingCosts",
        presentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
            planningDisplayCurrency));
    model.addAttribute(
        "displayTotalAnnualCosts",
        presentation.toDisplay(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualLivingCosts",
        presentation.toDisplay(assumptions.annualLivingExpenses(), planningDisplayCurrency));
    model.addAttribute(
        "displayMonthlyTotalCosts",
        presentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .add(assumptions.annualDiscretionaryExpenses())
                .divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP),
            planningDisplayCurrency));
    model.addAttribute(
        "displayDiscretionaryExpenses",
        presentation.toDisplay(assumptions.annualDiscretionaryExpenses(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualPension",
        presentation.toDisplay(assumptions.annualPension(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualEmploymentIncome",
        presentation.toDisplay(assumptions.annualEmploymentIncome(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualPreRetirementContribution",
        presentation.toDisplay(
            assumptions.annualPreRetirementContribution(), planningDisplayCurrency));
    model.addAttribute(
        "displayManualRentalIncome",
        presentation.toDisplay(
            assumptions.projectedIncomePolicy().manualRentalIncome(), planningDisplayCurrency));
    model.addAttribute(
        "displayManualBondCashIncome",
        presentation.toDisplay(
            assumptions.projectedIncomePolicy().manualBondCashIncome(), planningDisplayCurrency));
    Map<Long, BigDecimal> displayEventAmounts = new LinkedHashMap<>();
    assumptions
        .futureEvents()
        .forEach(
            event ->
                displayEventAmounts.put(
                    event.id(), presentation.toDisplay(event.amount(), planningDisplayCurrency)));
    model.addAttribute("displayEventAmounts", displayEventAmounts);
    return "simulation-plan-edit";
  }

  @PostMapping("/simulation/plans")
  public String savePlan(@Valid @ModelAttribute SimulationPlanSaveForm form) {
    int currentYear = Year.now(clock).getValue();
    Long portfolioId = form.getPortfolioId();
    Long planId = form.getPlanId();
    var planDetails = planId == null ? null : plans.details(portfolioId, planId);
    var storedAssumptions = planDetails == null ? null : planDetails.assumptions();
    SimulationAssumptions a =
        requestMapper.mapSaveForm(storedAssumptions, form.mappingInput(), currentYear);
    // Existing-plan edits preserve its reviewed baseline. Live state becomes a frozen baseline
    // only when creating a plan or explicitly rebaselining it.
    var liveProfile = profiles.loadProfile(portfolioId);
    var planningBaseline =
        planId != null && !form.isSaveAs()
            ? planDetails.baseline()
            : liveProfile == null ? null : PlanningBaseline.fromProfile(liveProfile, currentYear);
    Long savedPlanId =
        commands.savePlan(
            portfolioId, planId, form.getName(), a, planningBaseline, form.isSaveAs());
    CurrencyType returnCurrency =
        form.getReturnPlanningDisplayCurrency() == null
            ? form.getPlanningDisplayCurrency()
            : form.getReturnPlanningDisplayCurrency();
    return "redirect:/simulation?portfolioId="
        + portfolioId
        + (savedPlanId == null ? "" : "&planId=" + savedPlanId)
        + "&planningDisplayCurrency="
        + returnCurrency
        + "&selectedScenario="
        + form.getSelectedScenario();
  }

  @PostMapping("/simulation/plans/{planId}/events")
  public String saveEvent(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @RequestParam(required = false) Long eventId,
      @RequestParam int year,
      @RequestParam String name,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) BigDecimal canonicalAmount,
      @RequestParam(defaultValue = "false") boolean amountEdited,
      @RequestParam SimulationEventType type,
      @RequestParam(required = false) String notes,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    commands.saveEvent(
        portfolioId,
        planId,
        eventId,
        year,
        name,
        requestMapper.resolveDisplayedMoney(
            amount, canonicalAmount, amountEdited, planningDisplayCurrency, BigDecimal.ZERO),
        type,
        notes);
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{planId}/events/{eventId}/delete")
  public String deleteEvent(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @PathVariable Long eventId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    commands.deleteEvent(portfolioId, planId, eventId);
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{id}/delete")
  public String deletePlan(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long currentPlanId,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    commands.deletePlan(portfolioId, id);
    Long remainingPlanId =
        java.util.Objects.equals(id, currentPlanId)
            ? plans.resolvePlanId(portfolioId, null).orElse(null)
            : currentPlanId;
    return returnToEdit
        ? SimulationRedirects.editPlan(
            portfolioId, remainingPlanId, planningDisplayCurrency, selectedScenario)
        : SimulationRedirects.simulation(
            portfolioId, remainingPlanId, planningDisplayCurrency, selectedScenario);
  }
}
