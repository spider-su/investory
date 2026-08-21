package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.planning.*;
import com.smartbox.investory.retirement.planning.PlanningPresentation;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RetirementSimulationController {
  private final InvestmentProfileFacade profiles;
  private final RetirementSimulation simulations;
  private final SimulationPlanService plans;
  private final SustainableSpendingAnalysisService sustainableSpending;
  private final SimulationSensitivityAnalysisService sensitivity;
  private final RetirementAgeAnalysisService retirementAgeAnalysis;
  private final PlanningTimelineFacade planningTimeline;
  private final PlanningCurrencyPresentationService planningPresentation;
  private final ForwardSimulationInputService forwardInputs;
  private final AnnualPlanningRolloverService rollover;
  private final PlanningReconciliationService reconciliation;
  private final Clock clock;

  @Autowired(required = false)
  private PlanEditorPreviewService planEditorPreview;

  @Value("${develop.mode:true}")
  private boolean developMode = true;

  @Autowired
  public RetirementSimulationController(
      InvestmentProfileFacade profiles,
      RetirementSimulation simulations,
      SimulationPlanService plans,
      SustainableSpendingAnalysisService sustainableSpending,
      SimulationSensitivityAnalysisService sensitivity,
      RetirementAgeAnalysisService retirementAgeAnalysis,
      PlanningTimelineFacade planningTimeline,
      PlanningCurrencyPresentationService planningPresentation,
      ForwardSimulationInputService forwardInputs,
      AnnualPlanningRolloverService rollover,
      PlanningReconciliationService reconciliation,
      Clock clock) {
    this.profiles = profiles;
    this.simulations = simulations;
    this.plans = plans;
    this.sustainableSpending = sustainableSpending;
    this.sensitivity = sensitivity;
    this.retirementAgeAnalysis = retirementAgeAnalysis;
    this.planningTimeline = planningTimeline;
    this.planningPresentation = planningPresentation;
    this.forwardInputs = forwardInputs;
    this.rollover = rollover;
    this.reconciliation = reconciliation;
    this.clock = clock;
  }

  public RetirementSimulationController(
      InvestmentProfileFacade profiles,
      RetirementSimulation simulations,
      SimulationPlanService plans,
      SustainableSpendingAnalysisService sustainableSpending,
      SimulationSensitivityAnalysisService sensitivity,
      RetirementAgeAnalysisService retirementAgeAnalysis,
      PlanningTimelineFacade planningTimeline,
      PlanningCurrencyPresentationService planningPresentation,
      Clock clock) {
    this(
        profiles,
        simulations,
        plans,
        sustainableSpending,
        sensitivity,
        retirementAgeAnalysis,
        planningTimeline,
        planningPresentation,
        new ForwardSimulationInputService(
            new ForwardSimulationContextFactory(clock),
            new CurrentYearProjectionBridge(clock, simulations)),
        new AnnualPlanningRolloverService(planningTimeline, clock),
        null,
        clock);
  }

  /** Compatibility constructor for tests/callers that already provide forward-input service. */
  public RetirementSimulationController(
      InvestmentProfileFacade profiles,
      RetirementSimulation simulations,
      SimulationPlanService plans,
      SustainableSpendingAnalysisService sustainableSpending,
      SimulationSensitivityAnalysisService sensitivity,
      RetirementAgeAnalysisService retirementAgeAnalysis,
      PlanningTimelineFacade planningTimeline,
      PlanningCurrencyPresentationService planningPresentation,
      ForwardSimulationInputService forwardInputs,
      Clock clock) {
    this(
        profiles,
        simulations,
        plans,
        sustainableSpending,
        sensitivity,
        retirementAgeAnalysis,
        planningTimeline,
        planningPresentation,
        forwardInputs,
        new AnnualPlanningRolloverService(planningTimeline, clock),
        null,
        clock);
  }

  @GetMapping("/simulation")
  public String simulation(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(required = false) Integer currentAge,
      @RequestParam(required = false) Integer endAge,
      @RequestParam(required = false) BigDecimal annualExpenses,
      @RequestParam(required = false) BigDecimal annualExpensesCanonical,
      @RequestParam(defaultValue = "false") boolean annualExpensesEdited,
      @RequestParam(required = false) BigDecimal discretionaryExpenses,
      @RequestParam(required = false) BigDecimal discretionaryExpensesCanonical,
      @RequestParam(defaultValue = "false") boolean discretionaryExpensesEdited,
      @RequestParam(required = false) BigDecimal inflation,
      @RequestParam(required = false) BigDecimal rentalIncomeGrowth,
      @RequestParam(required = false) BigDecimal spendingGrowth,
      @RequestParam(required = false) SimulationFundingStrategy fundingStrategy,
      @RequestParam(required = false) BigDecimal safeReserveYears,
      @RequestParam(required = false) BigDecimal equityHarvestMinimumReturn,
      @RequestParam(required = false) BigDecimal equityGainHarvest,
      @RequestParam(required = false) Boolean allowEmergencyEquityWithdrawal,
      @RequestParam(required = false) BigDecimal cashReturn,
      @RequestParam(required = false) BigDecimal fixedIncomeReturn,
      @RequestParam(required = false) BigDecimal equityReturn,
      @RequestParam(required = false) BigDecimal realEstateReturn,
      @RequestParam(required = false) BigDecimal otherReturn,
      @RequestParam(required = false) Integer pensionStartAge,
      @RequestParam(required = false) BigDecimal annualPension,
      @RequestParam(required = false) BigDecimal annualPensionCanonical,
      @RequestParam(defaultValue = "false") boolean annualPensionEdited,
      @RequestParam(required = false) BigDecimal capitalGainTaxRate,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) CurrencyType submittedPlanningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    AnnualPlanningRolloverResult rolloverResult = rollover.rollover(portfolioId);
    // Optional assumption parameters are retained as transient/query overrides for legacy deep
    // links. Saved plans remain the canonical source when an override is not supplied.
    int requestedCurrentAge = currentAge == null ? 40 : currentAge;
    int requestedEndAge = endAge == null ? 95 : endAge;
    var profile = profiles.loadProfile(portfolioId);
    var defaults =
        SimulationAssumptions.defaults(
            profile, requestedCurrentAge, requestedEndAge, Year.now(clock).getValue());
    var base = planId == null ? defaults : plans.assumptions(portfolioId, planId);
    CurrencyType submittedCurrency =
        submittedPlanningDisplayCurrency == null
            ? planningDisplayCurrency
            : submittedPlanningDisplayCurrency;
    var assumptions =
        new SimulationAssumptions(
            currentAge == null ? base.currentAge() : currentAge,
            endAge == null ? base.endAge() : endAge,
            resolveDisplayedMoney(
                annualExpenses,
                annualExpensesCanonical,
                annualExpensesEdited,
                submittedCurrency,
                base.annualLivingExpenses()),
            percentInputToRate(inflation, base.inflationRate()),
            base.cashReturnRate(),
            base.fixedIncomeReturnRate(),
            percentInputToRate(equityReturn, base.equityReturnRate()),
            base.realEstateReturnRate(),
            base.otherReturnRate(),
            pensionStartAge == null ? base.pensionStartAge() : pensionStartAge,
            resolveDisplayedMoney(
                annualPension,
                annualPensionCanonical,
                annualPensionEdited,
                submittedCurrency,
                base.annualPension()),
            percentInputToRate(capitalGainTaxRate, base.capitalGainTaxRate()),
            base.startYear(),
            resolveDisplayedMoney(
                discretionaryExpenses,
                discretionaryExpensesCanonical,
                discretionaryExpensesEdited,
                submittedCurrency,
                base.annualDiscretionaryExpenses()),
            base.futureEvents(),
            percentInputToRate(rentalIncomeGrowth, base.rentalIncomeGrowthRate()),
            percentInputToRate(spendingGrowth, base.spendingGrowthRate()),
            SimulationFundingStrategy.SIMPLE_WATERFALL,
            base.safeReserveYears(),
            base.equityHarvestMinimumReturnRate(),
            base.equityGainHarvestRate(),
            base.allowEmergencyEquityWithdrawal(),
            base.retirementAge(),
            base.annualEmploymentIncome(),
            base.annualPreRetirementContribution(),
            SimulationAssumptions.DEFAULT_FUNDING_ORDER,
            base.expenseProfile());
    var projection = project(profile, assumptions);
    var projectedAssumptions = projection.assumptions();
    var projectedProfile = projection.profile();
    var results = projection.results();
    var summaries = projection.summaries();
    Map<Long, BigDecimal> displayEventAmounts = new LinkedHashMap<>();
    projectedAssumptions
        .futureEvents()
        .forEach(
            event ->
                displayEventAmounts.put(
                    event.id(),
                    planningPresentation.toDisplay(event.amount(), planningDisplayCurrency)));
    PlanningTimeline timeline =
        planningTimeline.loadForwardTimeline(portfolioId, profile, projection.forward(), selectedScenario);
    SimulationChartData canonicalCharts = SimulationChartData.from(results, projectedAssumptions);
    boolean currentYearCloseAllowed =
        timeline.years().stream()
            .filter(row -> row.state() == PlanningTimelineState.LIVE)
            .anyMatch(row -> row.year() < Year.now(clock).getValue());
    model.addAttribute("profile", profile);
    model.addAttribute(
        "displayProfile", planningPresentation.displayProfile(profile, planningDisplayCurrency));
    model.addAttribute("assumptions", assumptions);
    model.addAttribute("forwardAssumptions", projectedAssumptions);
    model.addAttribute("planningDisplayCurrency", planningDisplayCurrency);
    model.addAttribute("planningPresentation", planningPresentation);
    model.addAttribute(
        "expenseProfileValue", serializeExpenseProfile(assumptions.expenseProfile()));
    model.addAttribute(
        "displayAnnualExpenses",
        planningPresentation.toDisplay(
            assumptions.annualLivingExpenses(), planningDisplayCurrency));
    model.addAttribute(
        "displayMonthlyLivingCosts",
        planningPresentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
            planningDisplayCurrency));
    model.addAttribute(
        "displayTotalAnnualCosts",
        planningPresentation.toDisplay(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            planningDisplayCurrency));
    model.addAttribute(
        "displayDiscretionaryExpenses",
        planningPresentation.toDisplay(
            assumptions.annualDiscretionaryExpenses(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualPension",
        planningPresentation.toDisplay(assumptions.annualPension(), planningDisplayCurrency));
    model.addAttribute("displayEventAmounts", displayEventAmounts);
    model.addAttribute("plans", plans.list(portfolioId));
    model.addAttribute(
        "currentRevision", planId == null ? null : plans.currentRevision(portfolioId, planId));
    model.addAttribute(
        "revisionHistory",
        planId == null ? java.util.List.of() : plans.revisionHistory(portfolioId, planId));
    model.addAttribute("developMode", developMode);
    if (developMode && planEditorPreview != null) {
      var preview = planEditorPreview.preview(profile, assumptions, planningDisplayCurrency);
      model.addAttribute("planPreview", preview);
      model.addAttribute("currentRentalIncome", preview.rentalIncome());
      model.addAttribute("currentBondIncome", preview.bondIncome());
    }
    model.addAttribute("selectedPlanId", planId);
    model.addAttribute("selectedScenario", selectedScenario);
    model.addAttribute(
        "activePlanName", planId == null ? "Current assumptions" : plans.name(portfolioId, planId));
    model.addAttribute(
        "activePlanSummary",
        projectedAssumptions.currentAge()
            + " → "
            + projectedAssumptions.endAge()
            + " · Retire at "
            + projectedAssumptions.retirementAge()
            + " · Cost growth "
            + PlanningPresentation.percentage(projectedAssumptions.spendingGrowthRate())
            + " · "
            + PlanningPresentation.years(projectedAssumptions.safeReserveYears())
            + "-year reserve · "
            + PlanningPresentation.fundingStrategy(projectedAssumptions.fundingStrategy()));
    var displaySummaries =
        new LinkedHashMap<>(
            planningPresentation.displaySummaries(summaries, planningDisplayCurrency));
    model.addAttribute(
        "scenarioComparison",
        SimulationScenarioComparison.from(summaries, displaySummaries, selectedScenario));
    model.addAttribute("selectedSummary", displaySummaries.get(selectedScenario));
    var spendingAnalysis = sustainableSpending.analyze(projectedProfile, projectedAssumptions);
    var retirementAnalysis = retirementAgeAnalysis.analyze(projectedProfile, projectedAssumptions);
    model.addAttribute(
        "planRisks",
        planningPresentation.displayPlanRisks(
            sensitivity.analyze(projectedProfile, projectedAssumptions), planningDisplayCurrency));
    model.addAttribute(
        "planningFlexibility",
        planningPresentation.displayPlanningFlexibility(
            spendingAnalysis, retirementAnalysis, planningDisplayCurrency));
    model.addAttribute(
        "charts", planningPresentation.displayCharts(canonicalCharts, planningDisplayCurrency));
    model.addAttribute("timeline", timeline);
    var globalPlanProgress = planningTimeline.progress(timeline);
    model.addAttribute("planProgress", globalPlanProgress);
    model.addAttribute(
        "planProgressView",
        planningPresentation.displayPlanProgress(globalPlanProgress, planningDisplayCurrency));
    model.addAttribute("rolloverResult", rolloverResult);
    timeline.years().stream()
        .filter(row -> row.state() == PlanningTimelineState.LIVE)
        .findFirst()
        .ifPresent(
            row ->
                model.addAttribute(
                    "currentYearReview",
                    planningPresentation.displayCurrentYear(
                        row.current(), planningDisplayCurrency)));
    model.addAttribute(
        "timelineMoney",
        planningPresentation.displayTimelineMoney(
            timeline, planningDisplayCurrency, projectedAssumptions));
    model.addAttribute("currentYearCloseAllowed", currentYearCloseAllowed);
    return "simulation";
  }

  /** Read-only interpretation board over the same projected scenario results as Simulation. */
  @GetMapping("/analysis")
  public String analysis(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    var profile = profiles.loadProfile(portfolioId);
    var assumptions =
        planId == null
            ? SimulationAssumptions.defaults(profile, 40, 95, Year.now(clock).getValue())
            : plans.assumptions(portfolioId, planId);
    var projection = project(profile, assumptions);
    var forward = projection.forward();
    var projectedAssumptions = projection.assumptions();
    var projectedProfile = projection.profile();
    var results = projection.results();
    var summaries = projection.summaries();
    var displaySummaries =
        new LinkedHashMap<>(
            planningPresentation.displaySummaries(summaries, planningDisplayCurrency));
    var chartData =
        planningPresentation.displayCharts(
            SimulationChartData.from(results, projectedAssumptions), planningDisplayCurrency);
    var spendingAnalysis = sustainableSpending.analyze(projectedProfile, projectedAssumptions);
    var retirementAnalysis = retirementAgeAnalysis.analyze(projectedProfile, projectedAssumptions);
    var scenarioView =
        SimulationScenarioComparison.from(summaries, displaySummaries, selectedScenario);
    var riskView =
        planningPresentation.displayPlanRisks(
            sensitivity.analyze(projectedProfile, projectedAssumptions), planningDisplayCurrency);
    var flexibilityView =
        planningPresentation.displayPlanningFlexibility(
            spendingAnalysis, retirementAnalysis, planningDisplayCurrency);
    model.addAttribute(
        "analysisPage",
        new RetirementAnalysisPageView(
            planningDisplayCurrency,
            selectedScenario,
            displaySummaries.get(selectedScenario),
            scenarioView,
            riskView,
            flexibilityView,
            chartData,
            projectedAssumptions.currentAge() + " → " + projectedAssumptions.endAge()));
    model.addAttribute("profile", profile);
    model.addAttribute(
        "displayProfile", planningPresentation.displayProfile(profile, planningDisplayCurrency));
    model.addAttribute("planningDisplayCurrency", planningDisplayCurrency);
    model.addAttribute("selectedScenario", selectedScenario);
    model.addAttribute(
        "activePlanName", planId == null ? "Current assumptions" : plans.name(portfolioId, planId));
    model.addAttribute("selectedPlanId", planId);
    model.addAttribute("analysisHorizon", projectedAssumptions.currentAge() + " → " + projectedAssumptions.endAge());
    return "retirement-analysis";
  }

  /** Single boundary from projection mathematics to both retirement boards. */
  private SimulationProjection project(InvestmentProfile profile, SimulationAssumptions assumptions) {
    var forward = forwardInputs.prepare(profile, assumptions);
    var projectedAssumptions = forward.forwardAssumptions().orElse(assumptions);
    var projectedProfile = forward.bridgedProfile();
    var results =
        forward.forwardAssumptions().isPresent()
            ? simulations.compareScenarios(projectedProfile, projectedAssumptions)
            : new java.util.EnumMap<SimulationScenario, SimulationResult>(SimulationScenario.class);
    var summaries =
        new java.util.EnumMap<SimulationScenario, SimulationDecisionSummary>(SimulationScenario.class);
    results.forEach(
        (scenario, result) ->
            summaries.put(scenario, SimulationDecisionSummary.from(result, projectedAssumptions)));
    return new SimulationProjection(forward, projectedProfile, projectedAssumptions, results, summaries);
  }

  private record SimulationProjection(
      ForwardSimulationInput forward,
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      Map<SimulationScenario, SimulationResult> results,
      java.util.EnumMap<SimulationScenario, SimulationDecisionSummary> summaries) {}

  @GetMapping("/simulation/plan/edit")
  public String editPlan(
      @RequestParam(defaultValue = "1") Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      Model model) {
    var profile = profiles.loadProfile(portfolioId);
    int currentYear = Year.now(clock).getValue();
    SimulationAssumptions assumptions;
    String planName = "";
    if (planId == null) {
      assumptions = SimulationAssumptions.defaults(profile, 40, 95, currentYear);
    } else {
      assumptions = plans.assumptions(portfolioId, planId);
      planName = plans.name(portfolioId, planId);
    }
    model.addAttribute("profile", profile);
    model.addAttribute(
        "displayProfile", planningPresentation.displayProfile(profile, planningDisplayCurrency));
    model.addAttribute("assumptions", assumptions);
    model.addAttribute("existingPlan", planId != null);
    model.addAttribute(
        "currentPlanningAge",
        ForwardSimulationContextFactory.currentPlanningAge(assumptions, currentYear));
    model.addAttribute(
        "plannedRetirementYear", ForwardSimulationContextFactory.retirementYear(assumptions));
    model.addAttribute("planName", planName);
    model.addAttribute("selectedPlanId", planId);
    model.addAttribute("selectedScenario", selectedScenario);
    model.addAttribute("planningDisplayCurrency", planningDisplayCurrency);
    model.addAttribute("developMode", Boolean.valueOf(developMode));
    if (planEditorPreview != null) {
      var facts = planEditorPreview.currentFacts(profile);
      model.addAttribute(
          "currentRentalIncome",
          planningPresentation.toDisplay(facts.rentalIncome(), planningDisplayCurrency));
      model.addAttribute(
          "currentBondIncome",
          planningPresentation.toDisplay(facts.bondIncome(), planningDisplayCurrency));
    }
    model.addAttribute("plans", plans.list(portfolioId));
    model.addAttribute(
        "currentRevision", planId == null ? null : plans.currentRevision(portfolioId, planId));
    model.addAttribute(
        "revisionHistory",
        planId == null ? java.util.List.of() : plans.revisionHistory(portfolioId, planId));
    model.addAttribute(
        "displayAnnualExpenses",
        planningPresentation.toDisplay(
            assumptions.annualLivingExpenses(), planningDisplayCurrency));
    model.addAttribute(
        "displayMonthlyLivingCosts",
        planningPresentation.toDisplay(
            assumptions
                .annualLivingExpenses()
                .divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
            planningDisplayCurrency));
    model.addAttribute(
        "displayTotalAnnualCosts",
        planningPresentation.toDisplay(
            assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
            planningDisplayCurrency));
    model.addAttribute(
        "displayDiscretionaryExpenses",
        planningPresentation.toDisplay(
            assumptions.annualDiscretionaryExpenses(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualPension",
        planningPresentation.toDisplay(assumptions.annualPension(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualEmploymentIncome",
        planningPresentation.toDisplay(
            assumptions.annualEmploymentIncome(), planningDisplayCurrency));
    model.addAttribute(
        "displayAnnualPreRetirementContribution",
        planningPresentation.toDisplay(
            assumptions.annualPreRetirementContribution(), planningDisplayCurrency));
    Map<Long, BigDecimal> displayEventAmounts = new LinkedHashMap<>();
    assumptions
        .futureEvents()
        .forEach(
            event ->
                displayEventAmounts.put(
                    event.id(),
                    planningPresentation.toDisplay(event.amount(), planningDisplayCurrency)));
    model.addAttribute("displayEventAmounts", displayEventAmounts);
    if (developMode && planEditorPreview != null) {
      model.addAttribute(
          "planPreview", planEditorPreview.preview(profile, assumptions, planningDisplayCurrency));
    }
    return "simulation-plan-edit";
  }

  @PostMapping("/simulation/plans/preview")
  @ResponseBody
  public org.springframework.http.ResponseEntity<?> previewPlan(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam Map<String, String> fields) {
    if (!developMode || planEditorPreview == null)
      return org.springframework.http.ResponseEntity.notFound().build();
    try {
      var profile = profiles.loadProfile(portfolioId);
      var base =
          planId == null
              ? SimulationAssumptions.defaults(profile, 40, 95, Year.now(clock).getValue())
              : plans.assumptions(portfolioId, planId);
      return org.springframework.http.ResponseEntity.ok(
          planEditorPreview.preview(
              profile,
              previewAssumptions(base, fields, planningDisplayCurrency),
              planningDisplayCurrency));
    } catch (IllegalArgumentException | ArithmeticException ex) {
      return org.springframework.http.ResponseEntity.unprocessableEntity()
          .body(java.util.Map.of("available", false));
    }
  }

  private SimulationAssumptions previewAssumptions(
      SimulationAssumptions base, Map<String, String> fields, CurrencyType displayCurrency) {
    BigDecimal monthly = decimal(fields, "monthlyLivingCosts");
    BigDecimal annualLiving =
        monthly == null
            ? base.annualLivingExpenses()
            : planningPresentation.fromDisplay(
                monthly.multiply(BigDecimal.valueOf(12)),
                displayCurrency,
                base.annualLivingExpenses());
    return new SimulationAssumptions(
        integer(fields, "currentAge", base.currentAge()),
        integer(fields, "endAge", base.endAge()),
        annualLiving,
        percent(fields, "inflation", base.inflationRate()),
        percent(fields, "cashReturn", base.cashReturnRate()),
        percent(fields, "fixedIncomeReturn", base.fixedIncomeReturnRate()),
        percent(fields, "equityReturn", base.equityReturnRate()),
        base.realEstateReturnRate(),
        percent(fields, "otherReturn", base.otherReturnRate()),
        integer(fields, "pensionStartAge", base.pensionStartAge()),
        money(fields, "annualPension", displayCurrency, base.annualPension()),
        percent(fields, "capitalGainTaxRate", base.capitalGainTaxRate()),
        base.startYear(),
        money(fields, "discretionaryExpenses", displayCurrency, base.annualDiscretionaryExpenses()),
        base.futureEvents(),
        percent(fields, "rentalIncomeGrowth", base.rentalIncomeGrowthRate()),
        percent(fields, "spendingGrowth", base.spendingGrowthRate()),
        enumValue(
            fields, "fundingStrategy", SimulationFundingStrategy.class, base.fundingStrategy()),
        decimalOr(fields, "safeReserveYears", base.safeReserveYears()),
        percent(fields, "equityHarvestMinimumReturn", base.equityHarvestMinimumReturnRate()),
        percent(fields, "equityGainHarvest", base.equityGainHarvestRate()),
        booleanValue(
            fields, "allowEmergencyEquityWithdrawal", base.allowEmergencyEquityWithdrawal()),
        integer(fields, "retirementAge", base.retirementAge()),
        money(fields, "annualEmploymentIncome", displayCurrency, base.annualEmploymentIncome()),
        money(
            fields,
            "annualPreRetirementContribution",
            displayCurrency,
            base.annualPreRetirementContribution()),
        base.fundingOrder(),
        base.expenseProfile());
  }

  private BigDecimal money(
      Map<String, String> fields, String name, CurrencyType currency, BigDecimal fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : planningPresentation.fromDisplay(value, currency, fallback);
  }

  private static BigDecimal percent(Map<String, String> fields, String name, BigDecimal fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : percentInputToRate(value, fallback);
  }

  private static BigDecimal decimal(Map<String, String> fields, String name) {
    String raw = fields.get(name);
    return raw == null || raw.isBlank() ? null : new BigDecimal(raw);
  }

  private static BigDecimal decimalOr(
      Map<String, String> fields, String name, BigDecimal fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : value;
  }

  private static int integer(Map<String, String> fields, String name, int fallback) {
    BigDecimal value = decimal(fields, name);
    return value == null ? fallback : value.intValueExact();
  }

  private static boolean booleanValue(Map<String, String> fields, String name, boolean fallback) {
    String raw = fields.get(name);
    return raw == null || raw.isBlank() ? fallback : Boolean.parseBoolean(raw);
  }

  private static <T extends Enum<T>> T enumValue(
      Map<String, String> fields, String name, Class<T> type, T fallback) {
    String raw = fields.get(name);
    return raw == null || raw.isBlank() ? fallback : Enum.valueOf(type, raw);
  }

  @PostMapping("/simulation/timeline/past/{year}")
  public String createPastYear(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningTimeline.createHistoricalDraft(portfolioId, year);
    return simulationRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario);
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
    planningTimeline.refreshHistoricalDerivedValues(portfolioId, year);
    return "redirect:/simulation/timeline/"
        + year
        + "?portfolioId="
        + portfolioId
        + "&planId="
        + (planId == null ? "" : planId)
        + "&planningDisplayCurrency="
        + planningDisplayCurrency
        + "&selectedScenario="
        + selectedScenario;
  }

  // Compatibility overload for callers that do not carry plan/scenario context.
  public String createPastYear(Long portfolioId, int year, CurrencyType planningDisplayCurrency) {
    planningTimeline.createHistoricalDraft(portfolioId, year);
    return "redirect:/simulation?portfolioId="
        + portfolioId
        + "&planningDisplayCurrency="
        + planningDisplayCurrency;
  }

  @GetMapping("/simulation/timeline/{year}")
  // Compatibility overload for callers that do not carry plan/scenario context.
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
    model.addAttribute("planningPresentation", planningPresentation);
    model.addAttribute("selectedPlanId", planId);
    model.addAttribute("selectedScenario", selectedScenario);
    var stored = planningTimeline.pastYear(portfolioId, year);
    model.addAttribute(
        "planningYear", planningPresentation.display(stored, planningDisplayCurrency));
    model.addAttribute(
        "baselineRevision",
        stored.baselineRevisionId() == null || stored.baselinePlanId() == null
            ? null
            : plans.revision(portfolioId, stored.baselinePlanId(), stored.baselineRevisionId()));
    HistoricalReconciliation historicalReconciliation =
        reconciliation == null
            ? new HistoricalReconciliation(java.util.List.of())
            : reconciliation.reconcile(portfolioId, stored);
    model.addAttribute(
        "planningReconciliation",
        planningPresentation.displayReconciliation(
            historicalReconciliation, planningDisplayCurrency));
    model.addAttribute("yearReview", planningTimeline.yearReview(stored));
    Set<PlanningMetric> editableMetrics = EnumSet.noneOf(PlanningMetric.class);
    stored
        .values()
        .keySet()
        .forEach(
            metric -> {
              if (planningTimeline.isHistoricalMetricEditable(portfolioId, year, metric))
                editableMetrics.add(metric);
            });
    model.addAttribute("editableMetrics", editableMetrics);
    model.addAttribute(
        "planningCloseStatus", planningTimeline.historicalCloseStatus(portfolioId, year));
    PlanningMetricValue netWorth = stored.values().get(PlanningMetric.NET_WORTH);
    PlanningMetricValue marketAssets = stored.values().get(PlanningMetric.MARKET_ASSETS);
    model.addAttribute(
        "netWorthUnavailableWithMarketAssets",
        (netWorth == null || !netWorth.available())
            && marketAssets != null
            && marketAssets.available());
    return "planning-year";
  }

  public String planningYearDetail(
      Long portfolioId, int year, CurrencyType planningDisplayCurrency, Model model) {
    return planningYearDetail(
        portfolioId, year, planningDisplayCurrency, null, SimulationScenario.BASE, model);
  }

  @PostMapping("/simulation/timeline/baseline")
  public String setCurrentBaseline(
      @RequestParam Long portfolioId,
      @RequestParam Long planId,
      @RequestParam int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    var profile = profiles.loadProfile(portfolioId);
    planningTimeline.setCurrentBaseline(
        portfolioId,
        year,
        planId,
        plans.currentRevisionId(portfolioId, planId),
        profile,
        plans.assumptions(portfolioId, planId));
    return simulationRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario);
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
    planningTimeline.saveCurrentManualValue(
        portfolioId,
        year,
        metric,
        planningPresentation.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
        note);
    return simulationRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario);
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
      planningTimeline.saveDraftManualValue(
          portfolioId,
          year,
          metric,
          planningPresentation.fromDisplay(amount, planningDisplayCurrency, BigDecimal.ZERO),
          note);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return planningYearRedirect(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  // Compatibility overload for callers that do not carry plan/scenario context.
  public String savePastManual(
      Long portfolioId,
      int year,
      PlanningMetric metric,
      BigDecimal amount,
      String note,
      CurrencyType planningDisplayCurrency,
      RedirectAttributes redirectAttributes) {
    return savePastManual(
        portfolioId,
        year,
        metric,
        amount,
        note,
        planningDisplayCurrency,
        null,
        SimulationScenario.BASE,
        redirectAttributes);
  }

  @PostMapping("/simulation/timeline/current/{year}/close")
  public String closeCurrentYear(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    planningTimeline.closeCurrentYear(portfolioId, year, profiles.loadProfile(portfolioId));
    return simulationRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/timeline/past/{year}/close")
  // Compatibility overload for callers that do not carry plan/scenario context.
  public String closeHistoricalDraft(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    try {
      planningTimeline.closeHistoricalDraft(portfolioId, year);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return planningYearRedirect(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  public String closeHistoricalDraft(
      Long portfolioId,
      int year,
      CurrencyType planningDisplayCurrency,
      RedirectAttributes redirectAttributes) {
    return closeHistoricalDraft(
        portfolioId,
        year,
        planningDisplayCurrency,
        null,
        SimulationScenario.BASE,
        redirectAttributes);
  }

  @PostMapping("/simulation/timeline/{year}/reopen")
  // Compatibility overload for callers that do not carry plan/scenario context.
  public String reopenPlanningYear(
      @RequestParam Long portfolioId,
      @PathVariable int year,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario,
      RedirectAttributes redirectAttributes) {
    try {
      planningTimeline.reopenHistoricalYear(portfolioId, year);
    } catch (IllegalArgumentException | IllegalStateException error) {
      redirectAttributes.addFlashAttribute("planningError", error.getMessage());
    }
    return planningYearRedirect(
        portfolioId, year, planningDisplayCurrency, planId, selectedScenario);
  }

  public String reopenPlanningYear(
      Long portfolioId,
      int year,
      CurrencyType planningDisplayCurrency,
      RedirectAttributes redirectAttributes) {
    return reopenPlanningYear(
        portfolioId,
        year,
        planningDisplayCurrency,
        null,
        SimulationScenario.BASE,
        redirectAttributes);
  }

  public String savePlan(
      Long portfolioId,
      Long planId,
      String name,
      int currentAge,
      int endAge,
      int retirementAge,
      BigDecimal annualEmploymentIncome,
      BigDecimal annualPreRetirementContribution,
      BigDecimal annualExpenses,
      BigDecimal monthlyLivingCosts,
      BigDecimal discretionaryExpenses,
      BigDecimal inflation,
      BigDecimal rentalIncomeGrowth,
      BigDecimal spendingGrowth,
      SimulationFundingStrategy fundingStrategy,
      BigDecimal safeReserveYears,
      BigDecimal equityHarvestMinimumReturn,
      BigDecimal equityGainHarvest,
      boolean allowEmergencyEquityWithdrawal,
      BigDecimal cashReturn,
      BigDecimal fixedIncomeReturn,
      BigDecimal equityReturn,
      BigDecimal realEstateReturn,
      BigDecimal otherReturn,
      Integer pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      CurrencyType planningDisplayCurrency,
      CurrencyType returnPlanningDisplayCurrency,
      boolean saveAs,
      SimulationScenario selectedScenario) {
    return savePlan(
        portfolioId,
        planId,
        name,
        currentAge,
        endAge,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        annualExpenses,
        monthlyLivingCosts,
        discretionaryExpenses,
        inflation,
        rentalIncomeGrowth,
        spendingGrowth,
        fundingStrategy,
        "CASH,BONDS,STOCKS",
        "",
        safeReserveYears,
        equityHarvestMinimumReturn,
        equityGainHarvest,
        allowEmergencyEquityWithdrawal,
        cashReturn,
        fixedIncomeReturn,
        equityReturn,
        realEstateReturn,
        otherReturn,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        planningDisplayCurrency,
        returnPlanningDisplayCurrency,
        saveAs,
        selectedScenario);
  }

  public String savePlan(
      Long portfolioId,
      Long planId,
      String name,
      int currentAge,
      int endAge,
      BigDecimal annualExpenses,
      BigDecimal discretionaryExpenses,
      BigDecimal inflation,
      BigDecimal rentalIncomeGrowth,
      BigDecimal spendingGrowth,
      SimulationFundingStrategy fundingStrategy,
      BigDecimal safeReserveYears,
      BigDecimal equityHarvestMinimumReturn,
      BigDecimal equityGainHarvest,
      boolean allowEmergencyEquityWithdrawal,
      BigDecimal cashReturn,
      BigDecimal fixedIncomeReturn,
      BigDecimal equityReturn,
      BigDecimal realEstateReturn,
      BigDecimal otherReturn,
      Integer pensionStartAge,
      BigDecimal annualPension,
      BigDecimal capitalGainTaxRate,
      CurrencyType planningDisplayCurrency) {
    return savePlan(
        portfolioId,
        planId,
        name,
        currentAge,
        endAge,
        currentAge,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        annualExpenses,
        null,
        discretionaryExpenses,
        inflation,
        rentalIncomeGrowth,
        spendingGrowth,
        fundingStrategy,
        "CASH,BONDS,STOCKS",
        "",
        safeReserveYears,
        equityHarvestMinimumReturn,
        equityGainHarvest,
        allowEmergencyEquityWithdrawal,
        cashReturn,
        fixedIncomeReturn,
        equityReturn,
        realEstateReturn,
        otherReturn,
        pensionStartAge,
        annualPension,
        capitalGainTaxRate,
        planningDisplayCurrency,
        null,
        false,
        SimulationScenario.BASE);
  }

  @PostMapping("/simulation/plans")
  public String savePlan(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam String name,
      @RequestParam(defaultValue = "40") int currentAge,
      @RequestParam(defaultValue = "95") int endAge,
      @RequestParam(required = false) Integer retirementAge,
      @RequestParam(defaultValue = "0") BigDecimal annualEmploymentIncome,
      @RequestParam(defaultValue = "0") BigDecimal annualPreRetirementContribution,
      @RequestParam(required = false) BigDecimal annualExpenses,
      @RequestParam(required = false) BigDecimal monthlyLivingCosts,
      @RequestParam(defaultValue = "0") BigDecimal discretionaryExpenses,
      @RequestParam BigDecimal inflation,
      @RequestParam(defaultValue = "2") BigDecimal rentalIncomeGrowth,
      @RequestParam(defaultValue = "2.5") BigDecimal spendingGrowth,
      @RequestParam(defaultValue = "SIMPLE_WATERFALL") SimulationFundingStrategy fundingStrategy,
      @RequestParam(defaultValue = "CASH,BONDS,STOCKS") String fundingOrder,
      @RequestParam(defaultValue = "") String expenseProfile,
      @RequestParam(defaultValue = "5") BigDecimal safeReserveYears,
      @RequestParam(defaultValue = "7") BigDecimal equityHarvestMinimumReturn,
      @RequestParam(defaultValue = "75") BigDecimal equityGainHarvest,
      @RequestParam(defaultValue = "true") boolean allowEmergencyEquityWithdrawal,
      @RequestParam(defaultValue = "0") BigDecimal cashReturn,
      @RequestParam(defaultValue = "0") BigDecimal fixedIncomeReturn,
      @RequestParam BigDecimal equityReturn,
      @RequestParam(required = false) BigDecimal realEstateReturn,
      @RequestParam(defaultValue = "0") BigDecimal otherReturn,
      @RequestParam(required = false) Integer pensionStartAge,
      @RequestParam BigDecimal annualPension,
      @RequestParam(defaultValue = "0") BigDecimal capitalGainTaxRate,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) CurrencyType returnPlanningDisplayCurrency,
      @RequestParam(defaultValue = "false") boolean saveAs,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    var storedAssumptions = planId == null ? null : plans.assumptions(portfolioId, planId);
    var existingEvents =
        storedAssumptions != null
            ? storedAssumptions.futureEvents()
            : java.util.List.<SimulationEvent>of();
    int preservedCurrentAge =
        storedAssumptions == null ? currentAge : storedAssumptions.currentAge();
    BigDecimal annualLivingCostsInput =
        monthlyLivingCosts == null
            ? annualExpenses
            : monthlyLivingCosts.multiply(BigDecimal.valueOf(12));
    if (annualLivingCostsInput == null) annualLivingCostsInput = BigDecimal.ZERO;
    SimulationAssumptions a =
        new SimulationAssumptions(
                preservedCurrentAge,
                endAge,
                planningPresentation.fromDisplay(
                    annualLivingCostsInput, planningDisplayCurrency, BigDecimal.ZERO),
                percentInputToRate(inflation, BigDecimal.ZERO),
                percentInputToRate(cashReturn, BigDecimal.ZERO),
                percentInputToRate(fixedIncomeReturn, BigDecimal.ZERO),
                percentInputToRate(equityReturn, BigDecimal.ZERO),
                percentInputToRate(
                    realEstateReturn,
                    storedAssumptions == null
                        ? BigDecimal.ZERO
                        : storedAssumptions.realEstateReturnRate()),
                percentInputToRate(otherReturn, BigDecimal.ZERO),
                normalizePensionStartAge(pensionStartAge),
                planningPresentation.fromDisplay(
                    annualPension, planningDisplayCurrency, BigDecimal.ZERO),
                percentInputToRate(capitalGainTaxRate, BigDecimal.ZERO),
                storedAssumptions == null
                    ? Year.now(clock).getValue()
                    : storedAssumptions.startYear(),
                planningPresentation.fromDisplay(
                    discretionaryExpenses, planningDisplayCurrency, BigDecimal.ZERO),
                existingEvents,
                percentInputToRate(
                    rentalIncomeGrowth, SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_RATE),
                percentInputToRate(
                    spendingGrowth, SimulationAssumptions.DEFAULT_SPENDING_GROWTH_RATE),
                SimulationFundingStrategy.SIMPLE_WATERFALL,
                storedAssumptions == null
                    ? SimulationAssumptions.DEFAULT_SAFE_RESERVE_YEARS
                    : storedAssumptions.safeReserveYears(),
                percentInputToRate(
                    equityHarvestMinimumReturn,
                    SimulationAssumptions.DEFAULT_EQUITY_HARVEST_MINIMUM_RETURN_RATE),
                percentInputToRate(
                    equityGainHarvest, SimulationAssumptions.DEFAULT_EQUITY_GAIN_HARVEST_RATE),
                allowEmergencyEquityWithdrawal,
                retirementAge == null ? preservedCurrentAge : retirementAge,
                planningPresentation.fromDisplay(
                    annualEmploymentIncome, planningDisplayCurrency, BigDecimal.ZERO),
                planningPresentation.fromDisplay(
                    annualPreRetirementContribution, planningDisplayCurrency, BigDecimal.ZERO))
            .withFundingOrder(SimulationAssumptions.DEFAULT_FUNDING_ORDER)
            .withExpenseProfile(parseExpenseProfile(expenseProfile));
    Long savedPlanId =
        planId == null || saveAs
            ? plans.createId(portfolioId, name, a)
            : plans.updateId(portfolioId, planId, name, a);
    CurrencyType returnCurrency =
        returnPlanningDisplayCurrency == null
            ? planningDisplayCurrency
            : returnPlanningDisplayCurrency;
    return "redirect:/simulation?portfolioId="
        + portfolioId
        + (savedPlanId == null ? "" : "&planId=" + savedPlanId)
        + "&planningDisplayCurrency="
        + returnCurrency
        + "&selectedScenario="
        + selectedScenario;
  }

  private static List<FundingSource> parseFundingOrder(String value) {
    if (value == null || value.isBlank()) return SimulationAssumptions.DEFAULT_FUNDING_ORDER;
    try {
      return Arrays.stream(value.split(",")).map(String::trim).map(FundingSource::valueOf).toList();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown funding source", exception);
    }
  }

  private static String serializeExpenseProfile(ExpenseProfile profile) {
    return profile.steps().stream()
        .map(step -> step.fromYear() + ":" + step.factor().toPlainString())
        .reduce((left, right) -> left + ";" + right)
        .orElse("");
  }

  private static ExpenseProfile parseExpenseProfile(String value) {
    if (value == null || value.isBlank()) return ExpenseProfile.EMPTY;
    try {
      return new ExpenseProfile(
          Arrays.stream(value.split(";"))
              .map(String::trim)
              .map(
                  entry -> {
                    String[] parts = entry.split(":", -1);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    return new ExpenseProfileStep(
                        Integer.parseInt(parts[0].trim()), new BigDecimal(parts[1].trim()));
                  })
              .toList());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Invalid expense profile", exception);
    }
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
    plans.saveEvent(
        portfolioId,
        planId,
        eventId,
        year,
        name,
        resolveDisplayedMoney(
            amount, canonicalAmount, amountEdited, planningDisplayCurrency, BigDecimal.ZERO),
        type,
        notes);
    return returnToEdit
        ? editPlanRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : simulationRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{planId}/events/{eventId}/delete")
  public String deleteEvent(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @PathVariable Long eventId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    plans.deleteEvent(portfolioId, planId, eventId);
    return returnToEdit
        ? editPlanRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario)
        : simulationRedirect(portfolioId, planId, planningDisplayCurrency, selectedScenario);
  }

  @PostMapping("/simulation/plans/{id}/delete")
  public String deletePlan(
      @PathVariable Long id,
      @RequestParam Long portfolioId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(required = false) Long currentPlanId,
      @RequestParam(defaultValue = "false") boolean returnToEdit,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    plans.delete(portfolioId, id);
    Long remainingPlanId = java.util.Objects.equals(id, currentPlanId) ? null : currentPlanId;
    return returnToEdit
        ? editPlanRedirect(portfolioId, remainingPlanId, planningDisplayCurrency, selectedScenario)
        : simulationRedirect(
            portfolioId, remainingPlanId, planningDisplayCurrency, selectedScenario);
  }

  private static BigDecimal value(BigDecimal value, BigDecimal fallback) {
    return value == null ? fallback : value;
  }

  private static String planningYearRedirect(
      Long portfolioId, int year, CurrencyType planningDisplayCurrency) {
    return "redirect:/simulation/timeline/"
        + year
        + "?portfolioId="
        + portfolioId
        + "&planningDisplayCurrency="
        + planningDisplayCurrency;
  }

  private static int normalizePensionStartAge(Integer pensionStartAge) {
    return pensionStartAge == null ? Integer.MAX_VALUE : pensionStartAge;
  }

  private static String planningYearRedirect(
      Long portfolioId,
      int year,
      CurrencyType planningDisplayCurrency,
      Long planId,
      SimulationScenario selectedScenario) {
    return planningYearRedirect(portfolioId, year, planningDisplayCurrency)
        + (planId == null ? "" : "&planId=" + planId)
        + ((planId == null && selectedScenario == SimulationScenario.BASE)
            ? ""
            : "&selectedScenario=" + selectedScenario);
  }

  private static String simulationRedirect(
      Long portfolioId,
      Long planId,
      CurrencyType planningDisplayCurrency,
      SimulationScenario selectedScenario) {
    return "redirect:/simulation?portfolioId="
        + portfolioId
        + (planId == null ? "" : "&planId=" + planId)
        + "&planningDisplayCurrency="
        + planningDisplayCurrency
        + "&selectedScenario="
        + selectedScenario;
  }

  private static String editPlanRedirect(
      Long portfolioId,
      Long planId,
      CurrencyType planningDisplayCurrency,
      SimulationScenario selectedScenario) {
    return "redirect:/simulation/plan/edit?portfolioId="
        + portfolioId
        + (planId == null ? "" : "&planId=" + planId)
        + "&planningDisplayCurrency="
        + planningDisplayCurrency
        + "&selectedScenario="
        + selectedScenario;
  }

  static BigDecimal percentInputToRate(BigDecimal percent, BigDecimal fallback) {
    return percent == null ? fallback : percent.movePointLeft(2);
  }

  private BigDecimal resolveDisplayedMoney(
      BigDecimal displayAmount,
      BigDecimal canonicalAmount,
      boolean edited,
      CurrencyType display,
      BigDecimal fallback) {
    return !edited && canonicalAmount != null
        ? canonicalAmount
        : displayAmount == null
            ? fallback
            : planningPresentation.fromDisplay(displayAmount, display, fallback);
  }
}
