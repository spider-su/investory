package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Backend-authoritative canonical/display conversion for planning presentation only. */
@Service
public class PlanningCurrencyPresentationService {
  private static final DateTimeFormatter PLAN_PROGRESS_BOUNDARY =
      DateTimeFormatter.ofPattern("d MMM uuuu", java.util.Locale.ENGLISH);
  private final PlanningMoneyConversionService money;
  private final RetirementAnalysisPresentation analysisPresentation;

  public PlanningCurrencyPresentationService(CurrencyConversion rates, Clock clock) {
    this.money = new PlanningMoneyConversionService(rates, clock);
    this.analysisPresentation = new RetirementAnalysisPresentation(money);
  }

  public BigDecimal toDisplay(BigDecimal canonical, CurrencyType display) {
    return money.toDisplay(canonical, display);
  }

  public BigDecimal toDisplay(BigDecimal amount, CurrencyType source, CurrencyType display) {
    return money.toDisplay(amount, source, display);
  }

  public BigDecimal fromDisplay(
      BigDecimal displayAmount, CurrencyType display, BigDecimal fallback) {
    return money.fromDisplay(displayAmount, display, fallback);
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
                    toDisplay(summary.finalLiquidAssets(), display),
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
        displayReserves(charts.reserves(), display),
        charts.metadata());
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
    return analysisPresentation.displaySustainableSpending(analysis, display);
  }

  public SimulationSensitivityAnalysisMoney displaySensitivity(
      com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis analysis,
      CurrencyType display) {
    return analysisPresentation.displaySensitivity(analysis, display);
  }

  public PlanRiskView displayPlanRisks(
      com.smartbox.investory.retirement.simulation.SimulationSensitivityAnalysis analysis,
      CurrencyType display) {
    return analysisPresentation.displayPlanRisks(analysis, display);
  }

  public RetirementAgeAnalysisMoney displayRetirementAgeAnalysis(RetirementAgeAnalysis analysis) {
    return analysisPresentation.displayRetirementAgeAnalysis(analysis);
  }

  public PlanningFlexibilityMoney displayPlanningFlexibility(
      com.smartbox.investory.retirement.simulation.SustainableSpendingAnalysis spending,
      RetirementAgeAnalysis retirement,
      CurrencyType display) {
    return analysisPresentation.displayPlanningFlexibility(spending, retirement, display);
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
                                point.safeReserveTargetCoverageYears(),
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
          totalIncome = null,
          rentalIncome = null,
          bondIncome = null,
          fundingGap = null,
          reserveWithdrawal = null,
          longTermFunding = null,
          investmentWithdrawal = null,
          unfunded = null,
          reserveEnd = null,
          longTermCapitalEnd = null,
          investmentEnd = null,
          cashStart = null,
          cashEnd = null,
          bondsStart = null,
          bondsEnd = null,
          equitiesStart = null,
          equitiesEnd = null,
          realEstateStart = null,
          realEstateEnd = null,
          cashWithdrawal = null,
          bondWithdrawal = null,
          equityWithdrawal = null,
          realEstateWithdrawal = null,
          bondReturn = null,
          equityReturn = null,
          equityRefill = null;
      if ((row.state() == PlanningTimelineState.ACTUAL
              || row.state() == PlanningTimelineState.NEEDS_REVIEW)
          && row.past() != null) {
        annualCosts = annualCosts(row.past().values());
        rentalIncome =
            firstValue(
                row.past().values(), PlanningMetric.RENTAL_INCOME, PlanningMetric.PASSIVE_INCOME);
        bondIncome = planningValue(row.past().values(), PlanningMetric.BOND_INCOME);
        totalIncome = sumKnown(rentalIncome, bondIncome);
        cashEnd =
            firstValue(
                row.past().values(),
                PlanningMetric.CASH_RESERVE_VALUE,
                PlanningMetric.SAFE_RESERVE,
                PlanningMetric.MANUAL_LIQUID_RESERVE);
        reserveEnd = cashEnd;
        bondsEnd =
            firstValue(row.past().values(), PlanningMetric.BOND_VALUE, PlanningMetric.FIXED_INCOME);
        equitiesEnd =
            firstValue(row.past().values(), PlanningMetric.EQUITY, PlanningMetric.MARKET_ASSETS);
        realEstateEnd = planningValue(row.past().values(), PlanningMetric.REAL_ESTATE);
      } else if (row.state() == PlanningTimelineState.LIVE) {
        Map<PlanningMetric, PlanningMetricValue> currentValues = row.current().actualValues();
        Map<PlanningMetric, PlanningMetricValue> expectedValues = row.current().expectedValues();
        annualCosts = annualCosts(currentValues);
        if (annualCosts == null && assumptions != null)
          annualCosts = annualCostsFor(assumptions, row.year(), BigDecimal.ZERO);
        rentalIncome =
            firstValue(currentValues, PlanningMetric.RENTAL_INCOME, PlanningMetric.PASSIVE_INCOME);
        bondIncome = planningValue(currentValues, PlanningMetric.BOND_INCOME);
        BigDecimal employment =
            assumptions != null
                    && ForwardSimulationContextFactory.currentPlanningAge(assumptions, row.year())
                        < assumptions.retirementAge()
                ? assumptions.annualEmploymentIncome()
                : BigDecimal.ZERO;
        BigDecimal pension =
            assumptions != null
                    && ForwardSimulationContextFactory.currentPlanningAge(assumptions, row.year())
                        >= assumptions.pensionStartAge()
                ? assumptions.annualPension()
                : BigDecimal.ZERO;
        BigDecimal eventIncome =
            eventAmount(assumptions, row.year(), SimulationEventType.ONE_OFF_INCOME);
        BigDecimal eventExpenses =
            eventAmount(assumptions, row.year(), SimulationEventType.ONE_OFF_EXPENSE);
        annualCosts = annualCosts == null ? null : annualCosts.add(eventExpenses);
        totalIncome =
            employment.add(zero(rentalIncome)).add(zero(bondIncome)).add(pension).add(eventIncome);
        fundingGap = gap(annualCosts, totalIncome);
        cashStart =
            firstValue(
                currentValues,
                PlanningMetric.CASH_RESERVE_VALUE,
                PlanningMetric.MANUAL_LIQUID_RESERVE,
                PlanningMetric.SAFE_RESERVE);
        cashEnd =
            firstValue(
                expectedValues,
                PlanningMetric.CASH_RESERVE_VALUE,
                PlanningMetric.MANUAL_LIQUID_RESERVE,
                PlanningMetric.SAFE_RESERVE);
        if (cashEnd == null) cashEnd = cashStart;
        cashWithdrawal =
            cashStart == null || cashEnd == null
                ? null
                : cashStart.subtract(cashEnd).max(BigDecimal.ZERO);
        reserveEnd = cashEnd;
        bondsStart =
            firstValue(currentValues, PlanningMetric.BOND_VALUE, PlanningMetric.FIXED_INCOME);
        bondsEnd =
            firstValue(expectedValues, PlanningMetric.BOND_VALUE, PlanningMetric.FIXED_INCOME);
        if (bondsEnd == null) bondsEnd = bondsStart;
        equitiesStart =
            firstValue(currentValues, PlanningMetric.EQUITY, PlanningMetric.MARKET_ASSETS);
        equitiesEnd =
            firstValue(expectedValues, PlanningMetric.EQUITY, PlanningMetric.MARKET_ASSETS);
        if (equitiesEnd == null) equitiesEnd = equitiesStart;
        realEstateStart = planningValue(currentValues, PlanningMetric.REAL_ESTATE);
        realEstateEnd = planningValue(expectedValues, PlanningMetric.REAL_ESTATE);
        if (realEstateEnd == null) realEstateEnd = realEstateStart;
      } else if (row.state() == PlanningTimelineState.PROJECTED && row.projection() != null) {
        annualCosts = row.projection().totalExpenses();
        rentalIncome = row.projection().rentalIncome();
        bondIncome = row.projection().bondIncome();
        totalIncome = row.projection().totalIncome();
        SimulationFunding funding = row.projection().funding();
        fundingGap = funding.fundingGap();
        reserveWithdrawal = funding.reserveWithdrawal();
        longTermFunding = funding.longTermFunding();
        investmentWithdrawal = funding.investmentWithdrawal();
        unfunded = funding.unfunded();
        reserveEnd = funding.reserveEnd();
        longTermCapitalEnd = funding.longTermCapitalEnd();
        investmentEnd = funding.investmentEnd();
        cashStart = row.projection().cashStart();
        cashEnd = row.projection().cashEnd();
        bondsStart = row.projection().fixedIncomeStart();
        bondsEnd = row.projection().fixedIncomeEnd();
        equitiesStart = row.projection().equityStart();
        equitiesEnd = row.projection().equityEnd();
        realEstateStart = row.projection().realEstateStart();
        realEstateEnd = row.projection().realEstateEnd();
        cashWithdrawal = row.projection().manualLiquidReserveWithdrawal();
        bondWithdrawal = funding.longTermFunding();
        equityWithdrawal = row.projection().emergencyEquityWithdrawal();
        realEstateWithdrawal =
            zero(row.projection().realEstateStart())
                .subtract(zero(row.projection().realEstateEnd()))
                .max(BigDecimal.ZERO);
        bondReturn = row.projection().capitalizedBondReturn();
        equityReturn = row.projection().equityGain();
        equityRefill = row.projection().equityToFixedIncomeTransfer();
      }
      result.put(
          row.year(),
          new PlanningTimelineMoney(
              toDisplay(annualCosts, currency),
              toDisplay(totalIncome, currency),
              toDisplay(rentalIncome, currency),
              toDisplay(bondIncome, currency),
              toDisplay(fundingGap, currency),
              toDisplay(reserveWithdrawal, currency),
              toDisplay(longTermFunding, currency),
              toDisplay(investmentWithdrawal, currency),
              toDisplay(unfunded, currency),
              toDisplay(reserveEnd, currency),
              toDisplay(longTermCapitalEnd, currency),
              toDisplay(investmentEnd, currency),
              toDisplay(cashStart, currency),
              toDisplay(cashEnd, currency),
              toDisplay(bondsStart, currency),
              toDisplay(bondsEnd, currency),
              toDisplay(equitiesStart, currency),
              toDisplay(equitiesEnd, currency),
              toDisplay(realEstateStart, currency),
              toDisplay(realEstateEnd, currency),
              toDisplay(cashWithdrawal, currency),
              toDisplay(bondWithdrawal, currency),
              toDisplay(equityWithdrawal, currency),
              toDisplay(realEstateWithdrawal, currency),
              toDisplay(bondReturn, currency),
              toDisplay(equityReturn, currency),
              toDisplay(equityRefill, currency)));
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
    BigDecimal growth =
        BigDecimal.ONE.add(assumptions.effectiveSpendingGrowthRate()).pow(elapsedYears);
    return assumptions
        .annualLivingExpenses()
        .add(assumptions.annualDiscretionaryExpenses())
        .multiply(growth)
        .add(eventExpenses == null ? BigDecimal.ZERO : eventExpenses);
  }

  private static BigDecimal eventAmount(
      SimulationAssumptions assumptions, int year, SimulationEventType type) {
    if (assumptions == null) return BigDecimal.ZERO;
    return assumptions.futureEvents().stream()
        .filter(event -> event.year() == year && event.type() == type)
        .map(SimulationEvent::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static BigDecimal sumKnown(BigDecimal... values) {
    boolean known = false;
    BigDecimal total = BigDecimal.ZERO;
    for (BigDecimal value : values)
      if (value != null) {
        known = true;
        total = total.add(value);
      }
    return known ? total : null;
  }

  private static BigDecimal gap(BigDecimal expenses, BigDecimal income) {
    return expenses == null || income == null
        ? null
        : expenses.subtract(income).max(BigDecimal.ZERO);
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
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
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric... metrics) {
    for (PlanningMetric metric : metrics) {
      BigDecimal value = planningValue(values, metric);
      if (value != null) return value;
    }
    return null;
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
