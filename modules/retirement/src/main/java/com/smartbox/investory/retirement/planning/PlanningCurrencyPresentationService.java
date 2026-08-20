package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Backend-authoritative canonical/display conversion for planning presentation only. */
@Service
public class PlanningCurrencyPresentationService {
  private static final CurrencyType CANONICAL = CurrencyType.USD;
  private static final DateTimeFormatter PLAN_PROGRESS_BOUNDARY =
      DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH);
  private final CurrencyConversion rates;
  private final Clock clock;

  public PlanningCurrencyPresentationService(CurrencyConversion rates, Clock clock) {
    this.rates = rates;
    this.clock = clock;
  }

  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return canonical == null || display == CANONICAL
        ? canonical
        : rates.convertToBaseCurrency(canonical, display, CANONICAL, LocalDate.now(clock));
  }

  public BigDecimal toDisplay(BigDecimal amount, CurrencyType source, CurrencyType display) {
    return amount == null || source == display
        ? amount
        : rates.convertToBaseCurrency(amount, display, source, LocalDate.now(clock));
  }

  public BigDecimal fromDisplay(
      BigDecimal displayAmount, CurrencyType display, BigDecimal fallback) {
    return displayAmount == null
        ? fallback
        : display == CANONICAL
            ? displayAmount
            : rates.convertToBaseCurrency(displayAmount, CANONICAL, display, LocalDate.now(clock));
  }

  public PastPlanningYear display(PastPlanningYear past, CurrencyType display) {
    return new PastPlanningYear(
        past.year(),
        past.status(),
        past.closedAt(),
        past.baselinePlanId(),
        past.baselineRevisionId(),
        displayValues(past.values(), display),
        displayValues(past.expectedValues(), display));
  }

  public PlanProgressView displayPlanProgress(PlanProgress progress, CurrencyType display) {
    if (progress == null || !progress.available()) return PlanProgressView.unavailable();
    List<PlanProgressView.Point> points =
        progress.points().stream().map(point -> displayProgressPoint(point, display)).toList();
    PlanProgressPoint headline = progress.headline();
    return new PlanProgressView(
        true,
        signedMoney(toDisplay(headline.difference(), display), display),
        planProgressState(headline.status()),
        PLAN_PROGRESS_BOUNDARY.format(headline.boundaryDate()),
        points,
        points.size() <= 5 ? points : points.subList(points.size() - 5, points.size()));
  }

  public CurrentYearReview displayCurrentYear(CurrentPlanningYear current, CurrencyType display) {
    List<PlanningMetric> selected =
        List.of(
            PlanningMetric.NET_WORTH,
            PlanningMetric.SAFE_RESERVE,
            PlanningMetric.EQUITY,
            PlanningMetric.CORE_SPENDING,
            PlanningMetric.DISCRETIONARY_SPENDING,
            PlanningMetric.PASSIVE_INCOME);
    List<CurrentYearMetricReview> metrics =
        selected.stream().map(metric -> currentMetric(metric, current, display)).toList();
    List<String> missing =
        selected.stream()
            .filter(PlanningMetric::isRequiredForClose)
            .filter(
                metric ->
                    !current.actualValues().getOrDefault(metric, unavailable(metric)).available())
            .map(PlanningMetric::label)
            .toList();
    return new CurrentYearReview(
        missing.isEmpty() ? "Tracking complete" : "Manual input required",
        missing,
        metrics,
        current.baselinePlanId() != null);
  }

  public HistoricalReconciliationView displayReconciliation(
      HistoricalReconciliation reconciliation, CurrencyType display) {
    List<HistoricalReconciliationMetricView> metrics =
        reconciliation.metrics().stream()
            .map(
                metric ->
                    new HistoricalReconciliationMetricView(
                        metric.metric().label(),
                        displayPlanningValue(metric.planningValue(), metric.metric(), display),
                        displayPlanningValue(metric.referenceValue(), metric.metric(), display),
                        displayPlanningValue(metric.difference(), metric.metric(), display),
                        statusLabel(metric.status()),
                        qualityLabel(metric.quality()),
                        metric.source()))
            .toList();
    String summary =
        reconciliation.matchedCount()
            + " values match · "
            + reconciliation.differentCount()
            + " differ · planning-only values remain outside this comparison";
    return new HistoricalReconciliationView(summary, metrics);
  }

  private CurrentYearMetricReview currentMetric(
      PlanningMetric metric, CurrentPlanningYear current, CurrencyType display) {
    PlanningMetricValue actual = current.actualValues().get(metric);
    PlanningMetricValue expected = current.expectedValues().get(metric);
    boolean manual =
        actual != null
            && (actual.approvedValue() != null
                || actual.source() == PlanningValueSource.USER_OVERRIDE
                || actual.source() == PlanningValueSource.USER_ENTERED);
    return new CurrentYearMetricReview(
        metric,
        metric.label(),
        displayPlanningValue(actual, metric, display),
        displayPlanningValue(expected, metric, display),
        sourceLabel(actual, manual),
        manual,
        actual == null ? null : actual.note());
  }

  private String displayPlanningValue(
      PlanningMetricValue value, PlanningMetric metric, CurrencyType display) {
    if (value == null || value.value() == null) return "—";
    BigDecimal amount =
        metric.presentationType() == PlanningMetricPresentationType.MONEY
            ? toDisplay(value.value(), display)
            : value.value();
    return metric.isRatio()
        ? PlanningPresentation.percentage(amount)
        : PlanningPresentation.wholeNumber(amount);
  }

  private String displayPlanningValue(
      BigDecimal value, PlanningMetric metric, CurrencyType display) {
    if (value == null) return "—";
    BigDecimal amount =
        metric.presentationType() == PlanningMetricPresentationType.MONEY
            ? toDisplay(value, display)
            : value;
    return metric.isRatio()
        ? PlanningPresentation.percentage(amount)
        : PlanningPresentation.wholeNumber(amount);
  }

  private static String statusLabel(ReconciliationStatus status) {
    return switch (status) {
      case MATCHED -> "Matched";
      case DIFFERENT -> "Different";
      case NOT_AVAILABLE -> "Not available";
    };
  }

  private static String qualityLabel(ReconciliationQuality quality) {
    return switch (quality) {
      case EXACT -> "Exact source";
      case APPROXIMATE -> "Approximate source";
      case UNAVAILABLE -> "Accounting comparison unavailable";
      case MANUAL_ONLY -> "Manual planning value";
    };
  }

  private static String sourceLabel(PlanningMetricValue value, boolean manual) {
    if (value == null || !value.available()) return "Missing";
    if (manual) return "Manual planning input";
    return switch (value.source()) {
      case ACCOUNTING_DERIVED, PORTFOLIO_DERIVED, LONG_TERM_DERIVED -> "Live Investory data";
      case SIMULATION_BASELINE -> "Plan baseline";
      case UNAVAILABLE -> "Missing";
      case USER_ENTERED, USER_OVERRIDE -> "Manual planning input";
    };
  }

  private static PlanningMetricValue unavailable(PlanningMetric metric) {
    return new PlanningMetricValue(metric, null, null, PlanningValueSource.UNAVAILABLE, null);
  }

  public PlanningProfileMoney displayProfile(InvestmentProfile profile, CurrencyType display) {
    return new PlanningProfileMoney(
        toDisplay(profile.marketPortfolioValue(), profile.currency(), display),
        toDisplay(profile.longTermAssetValue(), profile.currency(), display),
        toDisplay(profile.totalNetWorth(), profile.currency(), display),
        toDisplay(profile.expectedLongTermAssetIncome(), profile.currency(), display),
        toDisplay(profile.liquidAssets(), profile.currency(), display),
        toDisplay(profile.illiquidAssets(), profile.currency(), display));
  }

  public Map<SimulationScenario, SimulationDecisionSummaryMoney> displaySummaries(
      Map<SimulationScenario, SimulationDecisionSummary> summaries, CurrencyType display) {
    Map<SimulationScenario, SimulationDecisionSummaryMoney> result =
        new EnumMap<>(SimulationScenario.class);
    summaries.forEach(
        (scenario, summary) ->
            result.put(
                scenario,
                new SimulationDecisionSummaryMoney(
                    scenario,
                    summary.failed(),
                    toDisplay(summary.finalNetWorth(), display),
                    toDisplay(summary.minimumLiquidAssets(), display),
                    toDisplay(summary.lowestNetWorth(), display),
                    toDisplay(summary.lifetimeActualWithdrawals(), display),
                    toDisplay(summary.totalUnfundedAmount(), display),
                    summary.firstYearPassiveIncomeCoverage(),
                    summary.minimumPassiveIncomeCoverage(),
                    summary.firstFailureYear(),
                    summary.firstFailureAge(),
                    toDisplay(summary.totalEquityHarvested(), display),
                    toDisplay(summary.totalEmergencyEquityWithdrawals(), display),
                    toDisplay(summary.totalManualLiquidReserveWithdrawals(), display),
                    toDisplay(summary.minimumManualLiquidReserve(), display),
                    summary.minimumSafeReserveCoverageYears(),
                    summary.yearsWithEquityHarvest(),
                    summary.yearsUsingEmergencyEquity(),
                    toDisplay(summary.finalSafeReserve(), display),
                    summary.recurringFundingGapRequired())));
    return result;
  }

  public SimulationChartData displayCharts(SimulationChartData charts, CurrencyType display) {
    Map<SimulationScenario, List<SimulationChartData.BalancePoint>> balances =
        new EnumMap<>(SimulationScenario.class);
    charts
        .balances()
        .forEach(
            (scenario, points) ->
                balances.put(
                    scenario,
                    points.stream()
                        .map(
                            point ->
                                new SimulationChartData.BalancePoint(
                                    point.year(),
                                    point.age(),
                                    toDisplay(point.netWorth(), display),
                                    toDisplay(point.liquidAssets(), display)))
                        .toList()));
    return new SimulationChartData(
        balances,
        charts.incomeSpending().stream()
            .map(
                point ->
                    new SimulationChartData.IncomeSpendingPoint(
                        point.year(),
                        toDisplay(point.recurringIncome(), display),
                        toDisplay(point.plannedSpending(), display)))
            .toList(),
        charts.composition().stream()
            .map(
                point ->
                    new SimulationChartData.CompositionPoint(
                        point.year(),
                        toDisplay(point.cash(), display),
                        toDisplay(point.apartments(), display),
                        toDisplay(point.bonds(), display),
                        toDisplay(point.equities(), display)))
            .toList(),
        displayFunding(charts.funding(), display),
        displayReserves(charts.reserves(), display));
  }

  private Map<SimulationScenario, List<SimulationChartData.FundingPoint>> displayFunding(
      Map<SimulationScenario, List<SimulationChartData.FundingPoint>> values,
      CurrencyType display) {
    Map<SimulationScenario, List<SimulationChartData.FundingPoint>> result =
        new EnumMap<>(SimulationScenario.class);
    values.forEach(
        (scenario, points) ->
            result.put(
                scenario,
                points.stream()
                    .map(
                        point ->
                            new SimulationChartData.FundingPoint(
                                point.year(),
                                point.age(),
                                toDisplay(point.passiveIncome(), display),
                                toDisplay(point.pensionIncome(), display),
                                toDisplay(point.plannedSpending(), display),
                                toDisplay(point.requiredPortfolioFunding(), display),
                                toDisplay(point.actualPortfolioWithdrawal(), display),
                                toDisplay(point.unfundedAmount(), display)))
                    .toList()));
    return result;
  }

  public SustainableSpendingAnalysisMoney displaySustainableSpending(
      com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis analysis,
      CurrencyType display) {
    var base = analysis.base();
    var conservative = analysis.conservative();
    BigDecimal current = toDisplay(analysis.currentRecurringSpending(), display);
    BigDecimal baseLimit = toDisplay(base.sustainableSpending(), display);
    BigDecimal conservativeLimit = toDisplay(conservative.sustainableSpending(), display);
    BigDecimal baseHeadroom = toDisplay(base.headroom(), display);
    BigDecimal conservativeHeadroom = toDisplay(conservative.headroom(), display);
    String baseLimitText = spendingLimit(base, baseLimit);
    String conservativeLimitText = spendingLimit(conservative, conservativeLimit);
    String baseHeadroomText = spendingHeadroom(base, baseHeadroom);
    String conservativeHeadroomText = spendingHeadroom(conservative, conservativeHeadroom);
    String basePercentageText = spendingPercentage(base);
    String conservativePercentageText = spendingPercentage(conservative);
    String interpretation;
    if (base.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .NO_SUSTAINABLE_SPENDING
        && conservative.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .NO_SUSTAINABLE_SPENDING) {
      interpretation =
          "Even zero total annual costs does not make the plan sustainable in either scenario.";
    } else if (base.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .NON_MONOTONIC_RESULT
        || conservative.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .NON_MONOTONIC_RESULT) {
      interpretation =
          "No reliable single spending limit can be determined under the tested assumptions.";
    } else if (base.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .UPPER_BOUND_NOT_FOUND
        || conservative.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .UPPER_BOUND_NOT_FOUND) {
      interpretation = "The spending limit exceeds the tested range in at least one scenario.";
    } else if (base.currentSpendingAboveLimit() && conservative.currentSpendingAboveLimit()) {
      interpretation =
          "Planned costs are above the spending limit in both Base and Conservative scenarios.";
    } else if (conservative.currentSpendingAboveLimit()) {
      interpretation =
          "Planned costs are about "
              + displayMoney(display, conservativeHeadroom.abs())
              + " per year over the Conservative spending limit. The Base plan remains within its limit.";
    } else if (base.currentSpendingAboveLimit()) {
      interpretation =
          "Planned costs are about "
              + displayMoney(display, baseHeadroom.abs())
              + " per year over the Base spending limit.";
    } else {
      interpretation =
          "Planned costs are within both Base and Conservative spending limits. Conservative extra capacity is about "
              + displayMoney(display, conservativeHeadroom.abs())
              + " per year.";
    }
    return new SustainableSpendingAnalysisMoney(
        PlanningPresentation.wholeNumber(current),
        baseLimitText,
        conservativeLimitText,
        baseHeadroomText,
        conservativeHeadroomText,
        basePercentageText,
        conservativePercentageText,
        base.currentSpendingAboveLimit(),
        conservative.currentSpendingAboveLimit(),
        interpretation);
  }

  private static String spendingLimit(
      com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis.ScenarioResult
          result,
      BigDecimal value) {
    return switch (result.state()) {
      case BOUNDARY_FOUND -> PlanningPresentation.wholeNumber(value);
      case UPPER_BOUND_NOT_FOUND -> "Above tested range";
      case NO_SUSTAINABLE_SPENDING -> "None found";
      case NON_MONOTONIC_RESULT -> "No reliable spending limit";
    };
  }

  private static String spendingHeadroom(
      com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis.ScenarioResult
          result,
      BigDecimal value) {
    return result.state()
            == com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .BOUNDARY_FOUND
        ? (value.signum() > 0 ? "+" : "") + PlanningPresentation.wholeNumber(value.abs())
        : "Not determined";
  }

  private static String spendingPercentage(
      com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis.ScenarioResult
          result) {
    if (result.state()
            != com.smartbox.investory.retirement.simulation.SustainableSpendingResultState
                .BOUNDARY_FOUND
        || result.headroomPercentage() == null) {
      return "Not determined";
    }
    return (result.headroomPercentage().signum() > 0 ? "+" : "")
        + PlanningPresentation.percentage(result.headroomPercentage().abs());
  }

  public SimulationSensitivityAnalysisMoney displaySensitivity(
      com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis analysis,
      CurrencyType display) {
    return new SimulationSensitivityAnalysisMoney(
        analysis.interpretation(),
        analysis.topDrivers(3).stream()
            .map(result -> displaySensitivityResult(result, display))
            .toList());
  }

  public PlanRiskView displayPlanRisks(
      com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis analysis,
      CurrencyType display) {
    var all =
        analysis.drivers().stream()
            .map(result -> displaySensitivityResult(result, display))
            .toList();
    var riskResults =
        analysis.drivers().stream()
            .filter(
                result ->
                    result.driver().category()
                        == com.smartbox.investory.retirement.simulation.SensitivityDriverCategory
                            .RISK)
            .toList();
    var leverResults =
        analysis.drivers().stream()
            .filter(
                result ->
                    result.driver().category()
                        == com.smartbox.investory.retirement.simulation.SensitivityDriverCategory
                            .POLICY_LEVER)
            .toList();
    var risks =
        riskResults.stream().map(result -> displaySensitivityResult(result, display)).toList();
    var levers =
        leverResults.stream().map(result -> displaySensitivityResult(result, display)).toList();
    String interpretation = riskInterpretation(analysis, riskResults);
    return new PlanRiskView(interpretation, risks.stream().limit(3).toList(), risks, levers);
  }

  public RetirementAgeAnalysisMoney displayRetirementAgeAnalysis(RetirementAgeAnalysis analysis) {
    var base = displayRetirementScenario(analysis.base());
    var conservative = displayRetirementScenario(analysis.conservative());
    String interpretation;
    if (analysis.conservative().state() == RetirementTimingResultState.NO_SUSTAINABLE_AGE
        && analysis.base().state() == RetirementTimingResultState.NO_SUSTAINABLE_AGE) {
      interpretation =
          "No sustainable retirement age was found within the configured planning horizon in Base or Conservative scenarios.";
    } else if (analysis.conservative().state() == RetirementTimingResultState.NON_MONOTONIC_RESULT
        || analysis.base().state() == RetirementTimingResultState.NON_MONOTONIC_RESULT) {
      interpretation =
          "Retirement timing results are non-monotonic under the current assumptions; life events or funding-policy timing affect individual ages.";
    } else if (analysis.conservative().state() == RetirementTimingResultState.DELAY_REQUIRED) {
      interpretation =
          "The planned retirement age is not sustainable under Conservative assumptions. Sustainability begins at "
              + conservative.earliest()
              + ".";
    } else if (analysis.conservative().state() == RetirementTimingResultState.ALREADY_RETIRED) {
      interpretation = "The planned retirement age has passed the current planning boundary.";
    } else if (analysis.conservative().state()
        == RetirementTimingResultState.IMMEDIATE_RETIREMENT_AVAILABLE) {
      interpretation =
          "The plan supports immediate retirement under the Conservative scenario. Base: "
              + base.earliest()
              + ".";
    } else if (analysis.conservative().headroomYears() > 0) {
      interpretation =
          "The Conservative scenario supports retirement "
              + analysis.conservative().headroomYears()
              + " years earlier than planned. Base: "
              + analysis.base().headroomYears()
              + " years earlier.";
    } else {
      interpretation =
          "The planned retirement age is the earliest sustainable age under Conservative assumptions. Base: "
              + analysis.base().headroomYears()
              + " years earlier.";
    }
    return new RetirementAgeAnalysisMoney(interpretation, base, conservative);
  }

  public PlanningFlexibilityMoney displayPlanningFlexibility(
      com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return new PlanningFlexibilityMoney(
        displaySustainableSpending(spending, display), displayRetirementAgeAnalysis(retirement));
  }

  private static RetirementAgeAnalysisMoney.Scenario displayRetirementScenario(
      RetirementAgeAnalysis.ScenarioResult result) {
    String earliest =
        result.earliestSustainableRetirementAge() == null
            ? "None"
            : "Age "
                + result.earliestSustainableRetirementAge()
                + " · "
                + result.earliestSustainableRetirementYear();
    String headroom =
        switch (result.state()) {
          case IMMEDIATE_RETIREMENT_AVAILABLE -> "Now";
          case ALREADY_RETIRED -> "Planned age has passed";
          case EARLIER_RETIREMENT_AVAILABLE -> result.headroomYears() + " years earlier";
          case PLANNED_AGE_IS_BOUNDARY -> "0 years";
          case DELAY_REQUIRED -> result.delayYears() + " years delay";
          case NO_SUSTAINABLE_AGE -> "—";
          case NON_MONOTONIC_RESULT -> "Non-monotonic result";
        };
    String state =
        switch (result.state()) {
          case IMMEDIATE_RETIREMENT_AVAILABLE -> "Immediate retirement available";
          case ALREADY_RETIRED -> "Planned retirement age has passed";
          case EARLIER_RETIREMENT_AVAILABLE -> "Earlier retirement available";
          case PLANNED_AGE_IS_BOUNDARY -> "Planned age is boundary";
          case DELAY_REQUIRED -> "Delay required";
          case NO_SUSTAINABLE_AGE -> "No sustainable age";
          case NON_MONOTONIC_RESULT -> "Non-monotonic result";
        };
    return new RetirementAgeAnalysisMoney.Scenario(
        "Age " + result.plannedRetirementAge() + " · " + result.plannedRetirementYear(),
        earliest,
        headroom,
        state,
        result.plannedRetirementSustainable(),
        result.earliestSustainableRetirementAge() != null);
  }

  private SimulationSensitivityAnalysisMoney.Driver displaySensitivityResult(
      com.smartbox.investory.retirement.simulation.SimulationSensitivityResult result,
      CurrencyType display) {
    var baseline = result.baseline().sustainability();
    var adverse = result.adverse().sustainability();
    String status;
    if (!result.baseline().sustainable()) {
      status = "Worsens existing plan shortfall";
    } else if (result.adverseCausesFailure()) {
      status =
          "Plan fails"
              + (adverse.firstFailureYear() == null ? "" : " in " + adverse.firstFailureYear());
    } else if (result.baseline().sustainable()) {
      status = "Plan remains sustainable";
    } else {
      status = "Plan remains unsustainable";
    }
    String reserve = reserveCoverageDisplay(baseline) + " → " + reserveCoverageDisplay(adverse);
    String wealth = signedMoney(toDisplay(result.finalNetWorthDelta(), display));
    return new SimulationSensitivityAnalysisMoney.Driver(
        result.driver().label(),
        result.perturbationLabel(),
        result.impact().name().replace('_', ' '),
        reserve,
        wealth,
        status);
  }

  private static String signedMoney(BigDecimal amount) {
    String value = PlanningPresentation.wholeNumber(amount.abs());
    return (amount.signum() < 0 ? "−" : "+") + value;
  }

  private static String signedMoney(BigDecimal amount, CurrencyType currency) {
    return signedMoney(amount) + " " + currency;
  }

  private PlanProgressView.Point displayProgressPoint(
      PlanProgressPoint point, CurrencyType display) {
    return new PlanProgressView.Point(
        point.year(), signedMoney(toDisplay(point.difference(), display), display));
  }

  private static String planProgressState(PlanProgressState state) {
    return switch (state) {
      case AHEAD -> "Ahead of plan";
      case BEHIND -> "Behind plan";
      case ON_PLAN -> "On plan";
      case UNAVAILABLE -> "Not available yet";
    };
  }

  private static String reserveCoverageDisplay(
      com.smartbox.investory.retirement.simulation.PlanSustainabilityAssessment assessment) {
    return assessment.recurringFundingGapRequired()
        ? PlanningPresentation.years(assessment.minimumSafeReserveCoverageYears()) + " years"
        : "Not required";
  }

  private static String riskInterpretation(
      com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis analysis,
      java.util.List<com.smartbox.investory.retirement.simulation.SimulationSensitivityResult>
          risks) {
    if (risks.isEmpty()) return "No active external risk assumptions were available for testing.";
    if (risks.stream()
        .allMatch(
            result ->
                result.impact()
                    == com.smartbox.investory.retirement.simulation.SensitivityImpact.NEGLIGIBLE))
      return "None of the tested risk assumptions materially threatens plan sustainability.";
    var top = risks.getFirst();
    if (analysis.baseline() != null && !analysis.baseline().sustainable())
      return top.driver().label()
          + " worsens the existing plan shortfall the most among tested risks.";
    if (top.impact() == com.smartbox.investory.retirement.simulation.SensitivityImpact.CRITICAL)
      return top.driver().label() + " is the largest tested risk to plan sustainability.";
    if (top.isWealthOnly())
      return top.driver().label()
          + " is the largest tested wealth effect; plan sustainability remains intact.";
    return top.driver().label() + " is the largest tested external risk to plan margin.";
  }

  private static String displayMoney(CurrencyType currency, BigDecimal amount) {
    return currency + " " + PlanningPresentation.wholeNumber(amount);
  }

  private Map<SimulationScenario, List<SimulationChartData.ReservePoint>> displayReserves(
      Map<SimulationScenario, List<SimulationChartData.ReservePoint>> values,
      CurrencyType display) {
    Map<SimulationScenario, List<SimulationChartData.ReservePoint>> result =
        new EnumMap<>(SimulationScenario.class);
    values.forEach(
        (scenario, points) ->
            result.put(
                scenario,
                points.stream()
                    .map(
                        point ->
                            new SimulationChartData.ReservePoint(
                                point.year(),
                                point.age(),
                                toDisplay(point.safeReserveEnd(), display),
                                toDisplay(point.safeReserveTarget(), display),
                                point.safeReserveCoverageYears(),
                                point.failed()))
                    .toList()));
    return result;
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency) {
    return displayTimelineMoney(timeline, currency, null);
  }

  public Map<Integer, PlanningTimelineMoney> displayTimelineMoney(
      PlanningTimeline timeline, CurrencyType currency, SimulationAssumptions assumptions) {
    Map<Integer, PlanningTimelineMoney> result = new LinkedHashMap<>();
    for (PlanningTimelineYear row : timeline.years()) {
      BigDecimal annualCosts = null,
          rentalIncome = null,
          incomeGap = null,
          fundingNeed = null,
          portfolioWithdrawal = null,
          unfunded = null,
          cash = null,
          safeReserve = null,
          bondsValue = null,
          bondsIncome = null,
          equityValue = null,
          equityGain = null,
          legacyFixedIncome = null,
          legacyEquity = null;
      if (row.state() == PlanningTimelineState.ACTUAL
          || row.state() == PlanningTimelineState.NEEDS_REVIEW) {
        annualCosts = annualCosts(row.past().values());
        rentalIncome =
            firstValue(
                row.past().values(), PlanningMetric.RENTAL_INCOME, PlanningMetric.PASSIVE_INCOME);
        incomeGap = difference(annualCosts, rentalIncome);
        fundingNeed = planningValue(row.past().values(), PlanningMetric.PORTFOLIO_FUNDING);
        portfolioWithdrawal = planningValue(row.past().values(), PlanningMetric.MARKET_WITHDRAWAL);
        cash = planningValue(row.past().values(), PlanningMetric.CASH_RESERVE_VALUE);
        safeReserve = planningValue(row.past().values(), PlanningMetric.SAFE_RESERVE);
        legacyFixedIncome = planningValue(row.past().values(), PlanningMetric.FIXED_INCOME);
        legacyEquity = planningValue(row.past().values(), PlanningMetric.EQUITY);
      } else if (row.state() == PlanningTimelineState.LIVE) {
        Map<PlanningMetric, PlanningMetricValue> currentValues = row.current().actualValues();
        annualCosts = annualCosts(currentValues);
        if (annualCosts == null && assumptions != null)
          annualCosts = annualCostsFor(assumptions, row.year(), BigDecimal.ZERO);
        rentalIncome =
            firstValue(currentValues, PlanningMetric.RENTAL_INCOME, PlanningMetric.PASSIVE_INCOME);
        incomeGap = difference(annualCosts, rentalIncome);
        fundingNeed = planningValue(currentValues, PlanningMetric.PORTFOLIO_FUNDING);
        portfolioWithdrawal = planningValue(currentValues, PlanningMetric.MARKET_WITHDRAWAL);
        cash = planningValue(currentValues, PlanningMetric.CASH_RESERVE_VALUE);
        safeReserve = planningValue(currentValues, PlanningMetric.SAFE_RESERVE);
        bondsValue = planningValue(currentValues, PlanningMetric.BOND_VALUE);
        bondsIncome = planningValue(currentValues, PlanningMetric.BOND_INCOME);
        equityValue = planningValue(currentValues, PlanningMetric.EQUITY);
        legacyFixedIncome = planningValue(currentValues, PlanningMetric.FIXED_INCOME);
        legacyEquity = equityValue;
      } else {
        annualCosts = row.projection().totalExpenses();
        rentalIncome = row.projection().rentalIncome();
        incomeGap = row.projection().incomeGap();
        fundingNeed = nonNegative(row.projection().requiredPortfolioFunding());
        portfolioWithdrawal = nonNegative(row.projection().actualPortfolioWithdrawal());
        unfunded = nonNegative(row.projection().unfundedAmount());
        cash = row.projection().cashEnd();
        safeReserve = row.projection().safeReserveEnd();
        bondsValue = row.projection().bondValueEnd();
        bondsIncome = row.projection().bondIncome();
        equityValue = row.projection().equityEnd();
        equityGain = row.projection().equityGain();
        legacyFixedIncome = row.projection().fixedIncomeEnd();
        legacyEquity = equityValue;
      }
      result.put(
          row.year(),
          new PlanningTimelineMoney(
              toDisplay(annualCosts, currency),
              toDisplay(rentalIncome, currency),
              toDisplay(incomeGap, currency),
              toDisplay(fundingNeed, currency),
              toDisplay(nonNegative(portfolioWithdrawal), currency),
              toDisplay(unfunded, currency),
              toDisplay(cash, currency),
              toDisplay(safeReserve, currency),
              toDisplay(bondsValue, currency),
              toDisplay(bondsIncome, currency),
              toDisplay(equityValue, currency),
              toDisplay(equityGain, currency),
              toDisplay(legacyFixedIncome, currency),
              toDisplay(legacyEquity, currency)));
    }
    return result;
  }

  private static BigDecimal annualCosts(Map<PlanningMetric, PlanningMetricValue> values) {
    BigDecimal core = planningValue(values, PlanningMetric.CORE_SPENDING);
    BigDecimal extras = planningValue(values, PlanningMetric.DISCRETIONARY_SPENDING);
    return core == null || extras == null ? null : core.add(extras);
  }

  private static BigDecimal annualCostsFor(
      SimulationAssumptions assumptions, int year, BigDecimal eventExpenses) {
    int elapsedYears = Math.max(0, year - assumptions.startYear());
    BigDecimal growth = BigDecimal.ONE.add(assumptions.spendingGrowthRate()).pow(elapsedYears);
    return assumptions
        .annualLivingExpenses()
        .add(assumptions.annualDiscretionaryExpenses())
        .multiply(growth)
        .add(eventExpenses == null ? BigDecimal.ZERO : eventExpenses);
  }

  private static BigDecimal nonNegative(BigDecimal value) {
    return value == null ? null : value.max(BigDecimal.ZERO);
  }

  private static BigDecimal difference(BigDecimal costs, BigDecimal income) {
    return costs == null || income == null ? null : costs.subtract(income).max(BigDecimal.ZERO);
  }

  private static BigDecimal planningValue(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values.get(metric);
    return value == null ? null : value.value();
  }

  private static BigDecimal firstValue(
      Map<PlanningMetric, PlanningMetricValue> values,
      PlanningMetric primary,
      PlanningMetric fallback) {
    BigDecimal value = planningValue(values, primary);
    return value == null ? planningValue(values, fallback) : value;
  }

  private Map<PlanningMetric, PlanningMetricValue> displayValues(
      Map<PlanningMetric, PlanningMetricValue> values, CurrencyType display) {
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    values.forEach(
        (metric, value) -> {
          if (metric == PlanningMetric.PASSIVE_INCOME) return;
          result.put(
              metric,
              new PlanningMetricValue(
                  metric,
                  metric.presentationType() == PlanningMetricPresentationType.MONEY
                      ? toDisplay(value.derivedValue(), display)
                      : value.derivedValue(),
                  metric.presentationType() == PlanningMetricPresentationType.MONEY
                      ? toDisplay(value.approvedValue(), display)
                      : value.approvedValue(),
                  value.source(),
                  value.note()));
        });
    return result;
  }
}
