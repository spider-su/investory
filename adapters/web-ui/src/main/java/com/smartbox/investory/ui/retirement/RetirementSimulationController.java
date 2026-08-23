package com.smartbox.investory.ui.retirement;

import com.smartbox.investory.retirement.api.InvestmentProfileFacade;
import com.smartbox.investory.retirement.planning.*;
import com.smartbox.investory.retirement.planning.PlanningPresentation;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.presentation.UiPresentation;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
  private final SimulationPlanService plans;
  private final PlanningTimelineFacade planningTimeline;
  private final PlanningCurrencyPresentationService planningPresentation;
  private final RetirementProjectionFacade projections;
  private final AnnualPlanningRolloverService rollover;
  private final PlanningReconciliationService reconciliation;
  private final Clock clock;
  private final PlanEditorInputNormalizer planEditorInputNormalizer;

  @Autowired(required = false)
  private PlanEditorPreviewService planEditorPreview;

  @Value("${develop.mode:true}")
  private boolean developMode = true;

  @Autowired
  public RetirementSimulationController(
      InvestmentProfileFacade profiles,
      SimulationPlanService plans,
      PlanningTimelineFacade planningTimeline,
      PlanningCurrencyPresentationService planningPresentation,
      RetirementProjectionFacade projections,
      AnnualPlanningRolloverService rollover,
      PlanningReconciliationService reconciliation,
      Clock clock) {
    this.profiles = profiles;
    this.plans = plans;
    this.planningTimeline = planningTimeline;
    this.planningPresentation = planningPresentation;
    this.projections = projections;
    this.rollover = rollover;
    this.reconciliation = reconciliation;
    this.clock = clock;
    this.planEditorInputNormalizer = new PlanEditorInputNormalizer(planningPresentation, clock);
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
        plans,
        planningTimeline,
        planningPresentation,
        new RetirementProjectionFacade(
            profiles,
            plans,
            new ForwardSimulationInputService(
                new ForwardSimulationContextFactory(clock),
                new CurrentYearProjectionBridge(clock, simulations)),
            simulations,
            clock),
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
        plans,
        planningTimeline,
        planningPresentation,
        new RetirementProjectionFacade(profiles, plans, forwardInputs, simulations, clock),
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
      @RequestParam(required = false) BigDecimal rentalIncomeGrowthSpread,
      @RequestParam(required = false) BigDecimal spendingGrowthSpread,
      @RequestParam(required = false) SimulationFundingStrategy fundingStrategy,
      @RequestParam(required = false) String fundingOrder,
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
    rollover.rollover(portfolioId);
    // Optional assumption parameters are retained as transient/query overrides for legacy deep
    // links. Saved plans remain the canonical source when an override is not supplied.
    int requestedCurrentAge = currentAge == null ? 40 : currentAge;
    int requestedEndAge = endAge == null ? 95 : endAge;
    var profile = profiles.loadProfile(portfolioId);
    Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
    var base =
        selectedPlanId == null
            ? SimulationAssumptions.defaults(
                profile, requestedCurrentAge, requestedEndAge, Year.now(clock).getValue())
            : plans.assumptions(portfolioId, selectedPlanId);
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
            percentInputToRate(rentalIncomeGrowthSpread, base.rentalIncomeGrowthSpread()),
            percentInputToRate(spendingGrowthSpread, base.spendingGrowthSpread()),
            fundingStrategy == null ? base.fundingStrategy() : fundingStrategy,
            safeReserveYears == null ? base.safeReserveYears() : safeReserveYears,
            equityHarvestMinimumReturn == null ? base.equityHarvestMinimumReturnRate()
                : percentInputToRate(equityHarvestMinimumReturn, base.equityHarvestMinimumReturnRate()),
            equityGainHarvest == null ? base.equityGainHarvestRate()
                : percentInputToRate(equityGainHarvest, base.equityGainHarvestRate()),
            allowEmergencyEquityWithdrawal == null ? base.allowEmergencyEquityWithdrawal()
                : allowEmergencyEquityWithdrawal,
            base.retirementAge(),
            base.annualEmploymentIncome(),
            base.annualPreRetirementContribution(),
            fundingOrder == null ? base.fundingOrder() : parseFundingOrder(fundingOrder),
            base.expenseProfile());
    var baseline = selectedPlanId == null ? null : plans.baseline(portfolioId, selectedPlanId);
    var projection = projections.project(profile, assumptions, baseline);
    var projectedAssumptions = projection.projectedAssumptions();
    var summaries = projection.summaries();
    PlanningTimeline timeline =
        planningTimeline.loadForwardTimeline(portfolioId, profile, projection.forward(), selectedScenario);
    boolean currentYearCloseAllowed =
        timeline.years().stream()
            .filter(row -> row.state() == PlanningTimelineState.LIVE)
            .anyMatch(row -> row.year() < Year.now(clock).getValue());
    var startingPosition = planningPresentation.displayProfile(profile, planningDisplayCurrency);
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
    String activePlanName =
        selectedPlanId == null ? "Current assumptions" : plans.name(portfolioId, selectedPlanId);
    String activePlanSummary =
        projectedAssumptions.currentAge()
            + " → "
            + projectedAssumptions.endAge()
            + " · Retire at "
            + projectedAssumptions.retirementAge()
            + " · Effective cost growth "
            + PlanningPresentation.percentage(projectedAssumptions.effectiveSpendingGrowthRate());
    var displaySummaries =
        new LinkedHashMap<>(
            planningPresentation.displaySummaries(summaries, planningDisplayCurrency));
    var timelineMoney =
        planningPresentation.displayTimelineMoney(timeline, planningDisplayCurrency, projectedAssumptions);
    var yearlySummaries = RetirementYearSummaryView.from(timeline, timelineMoney);
    var cashFlow = CashFlowSectionView.from(timeline, timelineMoney, projectedAssumptions);
    var chartData = RetirementSimulationChartView.from(timeline, timelineMoney, projectedAssumptions);
    var scenarioAssumptions = ScenarioEffectiveAssumptions.forScenario(
        projection.projectedProfile(), projectedAssumptions, selectedScenario);
    model.addAttribute(
        "simulationPage",
        new RetirementSimulationPageView(
            profile,
            startingPosition,
            assumptions,
            projectedAssumptions,
            planningDisplayCurrency,
            selectedPlanId,
            activePlanName,
            activePlanSummary,
            selectedScenario,
            List.of(SimulationScenario.values()),
            scenarioAssumptions,
            displaySummaries.get(selectedScenario),
            displayAnnualCosts,
            displayAnnualExpenses,
            displayDiscretionaryExpenses,
            displayAnnualPension,
            timeline,
            timelineMoney,
            yearlySummaries,
            cashFlow,
            currentYearCloseAllowed,
            chartData));
    return "simulation";
  }

  private BigDecimal displayMoney(BigDecimal amount, CurrencyType currency) {
    return planningPresentation.toDisplay(amount, currency)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros();
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
      assumptions = plans.assumptions(portfolioId, selectedPlanId);
      planName = plans.name(portfolioId, selectedPlanId);
    }
    model.addAttribute("profile", profile);
    model.addAttribute(
        "displayProfile", planningPresentation.displayProfile(profile, planningDisplayCurrency));
    model.addAttribute("assumptions", assumptions);
    model.addAttribute("planningBuckets", PlanningBuckets.fromProfileWithBondYield(profile,
        assumptions.equityReturnRate(),
        PlanningBuckets.baseBondYield(profile, assumptions.fixedIncomeReturnRate())));
    model.addAttribute("planStartYear", assumptions.planStartYear());
    model.addAttribute("ageAtPlanStart", assumptions.ageAtPlanStart());
    model.addAttribute(
        "plannedRetirementYear", ForwardSimulationContextFactory.retirementYear(assumptions));
    model.addAttribute("planName", planName);
    model.addAttribute("selectedPlanId", selectedPlanId);
    model.addAttribute("selectedScenario", selectedScenario);
    model.addAttribute("planningDisplayCurrency", planningDisplayCurrency);
    model.addAttribute("developMode", Boolean.valueOf(developMode));
    if (planEditorPreview != null) {
      var preview = planEditorPreview.preview(profile, assumptions, planningDisplayCurrency);
      model.addAttribute("currentRentalIncome", preview.rentalIncome());
      model.addAttribute("currentBondIncome", preview.bondIncome());
      model.addAttribute("plannedIncomeReferenceYear", preview.plannedIncomeReferenceYear());
      model.addAttribute("plannedRentalIncome", preview.plannedRentalIncome());
      model.addAttribute("plannedBondIncome", preview.plannedBondIncome());
      model.addAttribute("plannedInvestmentProfit", preview.plannedInvestmentProfit());
      model.addAttribute("plannedCapitalizedBondReturn", preview.plannedCapitalizedBondReturn());
      model.addAttribute("plannedPension", preview.plannedPension());
      model.addAttribute("plannedAnnualIncome", preview.plannedAnnualIncome());
      if (developMode) model.addAttribute("planPreview", preview);
    }
    model.addAttribute("plans", plans.list(portfolioId));
    model.addAttribute(
        "currentRevision",
        selectedPlanId == null ? null : plans.currentRevision(portfolioId, selectedPlanId));
    model.addAttribute(
        "revisionHistory",
        selectedPlanId == null
            ? java.util.List.of()
            : plans.revisionHistory(portfolioId, selectedPlanId));
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
    return "simulation-plan-edit";
  }

  @PostMapping("/simulation/plans/preview")
  @ResponseBody
  public org.springframework.http.ResponseEntity<?> previewPlan(
      @RequestParam Long portfolioId,
      @RequestParam(required = false) Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam Map<String, String> fields) {
    if (planEditorPreview == null)
      return org.springframework.http.ResponseEntity.notFound().build();
    try {
      var profile = profiles.loadProfile(portfolioId);
      Long selectedPlanId = plans.resolvePlanId(portfolioId, planId).orElse(null);
      var base =
          selectedPlanId == null
              ? SimulationAssumptions.defaults(profile, 40, 95, Year.now(clock).getValue())
              : plans.assumptions(portfolioId, selectedPlanId);
      var normalized =
          planEditorInputNormalizer.normalize(
              PlanEditorInput.from(fields), base, planningDisplayCurrency);
      return org.springframework.http.ResponseEntity.ok(
          java.util.Map.of(
              "available", true,
              "warnings", normalized.warnings(),
              "derived",
              java.util.Map.of(
                  "effectiveRentalGrowth",
                  UiPresentation.percentage(normalized.assumptions().effectiveRentalIncomeGrowthRate()),
                  "effectiveSpendingGrowth",
                  UiPresentation.percentage(normalized.assumptions().effectiveSpendingGrowthRate())),
              "preview",
              planEditorPreview.preview(profile, normalized.assumptions(), planningDisplayCurrency)));
    } catch (IllegalArgumentException | ArithmeticException ex) {
      return org.springframework.http.ResponseEntity.unprocessableEntity()
          .body(java.util.Map.of("available", false, "error", ex.getMessage()));
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
        integer(
            fields,
            "ageAtPlanStart",
            integer(fields, "currentAge", base.ageAtPlanStart())),
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
        integer(fields, "startYear", base.planStartYear()),
        money(fields, "discretionaryExpenses", displayCurrency, base.annualDiscretionaryExpenses()),
        base.futureEvents(),
        percent(fields, "rentalIncomeGrowthSpread", base.rentalIncomeGrowthSpread()),
        percent(fields, "spendingGrowthSpread", base.spendingGrowthSpread()),
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
    if (planId == null) {
      planningTimeline.createHistoricalDraft(portfolioId, year);
    } else {
      planningTimeline.seedHistoricalBaselineFromPlan(
          portfolioId,
          year,
          planId,
          plans.currentRevisionId(portfolioId, planId),
          profiles.loadProfile(portfolioId),
          plans.assumptions(portfolioId, planId));
    }
    return planningYearRedirect(
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
            : plans.assumptions(portfolioId, planId).planStartYear();
    planningTimeline.prefillHistoricalYears(portfolioId, startYear);
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

  @PostMapping("/simulation/plans/{planId}/rebaseline")
  public String rebaselinePlan(
      @RequestParam Long portfolioId,
      @PathVariable Long planId,
      @RequestParam(defaultValue = "PLN") CurrencyType planningDisplayCurrency,
      @RequestParam(defaultValue = "BASE") SimulationScenario selectedScenario) {
    plans.rebaseline(portfolioId, planId,
        PlanningBaseline.fromProfile(profiles.loadProfile(portfolioId), Year.now(clock).getValue()));
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
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
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
        planId == null ? currentAge : plans.assumptions(portfolioId, planId).currentAge(),
        planId == null ? null : plans.assumptions(portfolioId, planId).currentAge(),
        planId == null ? null : plans.assumptions(portfolioId, planId).startYear(),
        endAge,
        retirementAge,
        annualEmploymentIncome,
        annualPreRetirementContribution,
        annualExpenses,
        monthlyLivingCosts,
        discretionaryExpenses,
        inflation,
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
        "SOURCE", null, "SOURCE", null,
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
      BigDecimal rentalIncomeGrowthSpread,
      BigDecimal spendingGrowthSpread,
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
        null,
        null,
        endAge,
        currentAge,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        annualExpenses,
        null,
        discretionaryExpenses,
        inflation,
        rentalIncomeGrowthSpread,
        spendingGrowthSpread,
        "SOURCE", null, "SOURCE", null,
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
      @RequestParam(required = false) Integer ageAtPlanStart,
      @RequestParam(required = false) Integer startYear,
      @RequestParam(defaultValue = "95") int endAge,
      @RequestParam(required = false) Integer retirementAge,
      @RequestParam(defaultValue = "0") BigDecimal annualEmploymentIncome,
      @RequestParam(defaultValue = "0") BigDecimal annualPreRetirementContribution,
      @RequestParam(required = false) BigDecimal annualExpenses,
      @RequestParam(required = false) BigDecimal monthlyLivingCosts,
      @RequestParam(defaultValue = "0") BigDecimal discretionaryExpenses,
      @RequestParam BigDecimal inflation,
      @RequestParam(defaultValue = "2") BigDecimal rentalIncomeGrowthSpread,
      @RequestParam(defaultValue = "2.5") BigDecimal spendingGrowthSpread,
      @RequestParam(defaultValue = "SOURCE") String rentalIncomeMode,
      @RequestParam(required = false) BigDecimal manualRentalIncome,
      @RequestParam(defaultValue = "SOURCE") String bondCashIncomeMode,
      @RequestParam(required = false) BigDecimal manualBondCashIncome,
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
    int effectiveAgeAtPlanStart = ageAtPlanStart == null ? currentAge : ageAtPlanStart;
    int effectiveStartYear =
        startYear == null
            ? (storedAssumptions == null ? Year.now(clock).getValue() : storedAssumptions.startYear())
            : startYear;
    validateTemporalAnchor(
        effectiveStartYear,
        effectiveAgeAtPlanStart,
        endAge,
        retirementAge == null ? effectiveAgeAtPlanStart : retirementAge);
    BigDecimal annualLivingCostsInput =
        monthlyLivingCosts == null
            ? annualExpenses
            : monthlyLivingCosts.multiply(BigDecimal.valueOf(12));
    if (annualLivingCostsInput == null) annualLivingCostsInput = BigDecimal.ZERO;
    SimulationAssumptions legacyAssumptions =
        new SimulationAssumptions(
                effectiveAgeAtPlanStart,
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
                effectiveStartYear,
                planningPresentation.fromDisplay(
                    discretionaryExpenses, planningDisplayCurrency, BigDecimal.ZERO),
                existingEvents,
                percentInputToRate(
                    rentalIncomeGrowthSpread, SimulationAssumptions.DEFAULT_RENTAL_INCOME_GROWTH_SPREAD),
                percentInputToRate(
                    spendingGrowthSpread, SimulationAssumptions.DEFAULT_SPENDING_GROWTH_SPREAD),
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
                retirementAge == null ? effectiveAgeAtPlanStart : retirementAge,
                planningPresentation.fromDisplay(
                    annualEmploymentIncome, planningDisplayCurrency, BigDecimal.ZERO),
                planningPresentation.fromDisplay(
                    annualPreRetirementContribution, planningDisplayCurrency, BigDecimal.ZERO))
            .withFundingOrder(parseFundingOrder(fundingOrder))
            .withExpenseProfile(ExpenseProfile.EMPTY);
    SimulationAssumptions a =
        monthlyLivingCosts == null && annualExpenses != null
            ? legacyAssumptions.withExpenseProfile(parseExpenseProfile(expenseProfile))
            : planEditorInputNormalizer
            .normalize(
                PlanEditorInput.from(
                    Map.ofEntries(
                        Map.entry("ageAtPlanStart", String.valueOf(effectiveAgeAtPlanStart)),
                        Map.entry("startYear", String.valueOf(effectiveStartYear)),
                        Map.entry("endAge", String.valueOf(endAge)),
                        Map.entry("retirementAge", String.valueOf(retirementAge == null ? effectiveAgeAtPlanStart : retirementAge)),
                        Map.entry("monthlyLivingCosts", String.valueOf(monthlyLivingCosts == null ? annualLivingCostsInput.divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP) : monthlyLivingCosts)),
                        Map.entry("discretionaryExpenses", String.valueOf(discretionaryExpenses)),
                        Map.entry("inflation", String.valueOf(inflation)),
                        Map.entry("rentalIncomeGrowthSpread", String.valueOf(rentalIncomeGrowthSpread)),
                        Map.entry("spendingGrowthSpread", String.valueOf(spendingGrowthSpread)),
                        Map.entry("rentalIncomeMode", rentalIncomeMode),
                        Map.entry("manualRentalIncome", String.valueOf(manualRentalIncome == null ? "" : manualRentalIncome)),
                        Map.entry("bondCashIncomeMode", bondCashIncomeMode),
                        Map.entry("manualBondCashIncome", String.valueOf(manualBondCashIncome == null ? "" : manualBondCashIncome)),
                        Map.entry("equityReturn", String.valueOf(equityReturn)),
                        Map.entry("safeReserveYears", String.valueOf(safeReserveYears)),
                        Map.entry("equityHarvestThreshold", String.valueOf(equityHarvestMinimumReturn)),
                        Map.entry("equityHarvestShare", String.valueOf(equityGainHarvest)),
                        Map.entry("allowEmergencyEquityWithdrawal", String.valueOf(allowEmergencyEquityWithdrawal)),
                        Map.entry("annualEmploymentIncome", String.valueOf(annualEmploymentIncome)),
                        Map.entry("annualPreRetirementContribution", String.valueOf(annualPreRetirementContribution)),
                        Map.entry("annualPension", String.valueOf(annualPension)),
                        Map.entry("pensionStartAge", pensionStartAge == null ? "" : String.valueOf(pensionStartAge)),
                        Map.entry("expenseProfile", expenseProfile))),
                legacyAssumptions,
                planningDisplayCurrency)
            .assumptions();
    // Existing-plan edits preserve its reviewed baseline. Live state becomes a frozen baseline
    // only when creating a plan or explicitly rebaselining it.
    var liveProfile = profiles.loadProfile(portfolioId);
    var planningBaseline = planId != null && !saveAs
        ? plans.baseline(portfolioId, planId)
        : liveProfile == null
            ? null : PlanningBaseline.fromProfile(liveProfile, Year.now(clock).getValue());
    Long savedPlanId;
    if (planningBaseline == null) {
      savedPlanId = planId == null || saveAs
          ? plans.createId(portfolioId, name, a)
          : plans.updateId(portfolioId, planId, name, a);
    } else {
      savedPlanId = planId == null || saveAs
          ? plans.createId(portfolioId, name, a, planningBaseline)
          : plans.updateId(portfolioId, planId, name, a, planningBaseline);
    }
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

  private void validateTemporalAnchor(
      int startYear, int ageAtPlanStart, int endAge, int retirementAge) {
    int currentYear = Year.now(clock).getValue();
    int currentPlanningAge = ageAtPlanStart + currentYear - startYear;
    if (startYear > currentYear)
      throw new IllegalArgumentException("Plan start year cannot be in the future");
    if (ageAtPlanStart < 0 || endAge < currentPlanningAge)
      throw new IllegalArgumentException("Invalid plan temporal ages");
    if (retirementAge < ageAtPlanStart || retirementAge > endAge)
      throw new IllegalArgumentException("Invalid retirement age");
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
    Long remainingPlanId =
        java.util.Objects.equals(id, currentPlanId)
            ? plans.resolvePlanId(portfolioId, null).orElse(null)
            : currentPlanId;
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
