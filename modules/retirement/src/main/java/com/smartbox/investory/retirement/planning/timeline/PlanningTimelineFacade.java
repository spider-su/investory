package com.smartbox.investory.retirement.planning.timeline;

import com.smartbox.investory.investment.api.reporting.HistoricalPortfolioYear;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.profile.api.model.*;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.infrastructure.planningyear.*;
import com.smartbox.investory.retirement.planning.application.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.simulation.RetirementSimulation;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.shared.policy.FinancialPolicyDefaults;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write planning layer. It reads accounting/portfolio data but never writes it. Closed
 * planning values are copied here and never recomputed by later simulation changes.
 */
@Service
public class PlanningTimelineFacade {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final RetirementPlanningYearRepository years;
  private final RetirementPlanningYearStateCodec stateCodec;
  private final PlanningMetricDerivationService metrics;

  private final RetirementSimulation simulations;
  private final Clock clock;
  private final LongTermAssetProfileReader currentLongTermAssets;
  private final PlanningProgressService planningProgress;
  private final PlanningYearReviewService planningYearReviews;
  private final PlanningMoneyConversionService money;

  public PlanningTimelineFacade(
      RetirementPlanningYearRepository years,
      RetirementPlanningYearStateCodec stateCodec,
      PlanningMetricDerivationService metrics,
      RetirementSimulation simulations,
      Clock clock,
      LongTermAssetProfileReader currentLongTermAssets,
      PlanningProgressService planningProgress,
      PlanningYearReviewService planningYearReviews,
      PlanningMoneyConversionService money) {
    this.years = years;
    this.stateCodec = stateCodec;
    this.metrics = metrics;
    this.simulations = simulations;
    this.clock = clock;
    this.currentLongTermAssets = currentLongTermAssets;
    this.planningProgress = planningProgress;
    this.planningYearReviews = planningYearReviews;
    this.money = money;
  }

  /** Application read facade for planning progress and year review composition. */
  public PlanProgress progress(PlanningTimeline timeline) {
    return planningProgress.progressForTimeline(timeline);
  }

  /** Resolves reviewability from the canonical planning-year boundary. */
  @Transactional(readOnly = true)
  public YearReviewMode reviewMode(Long portfolioId, int year) {
    int current = activeCurrentYear(portfolioId);
    if (year == current) return YearReviewMode.LIVE;
    if (year < current && years.findByPortfolioIdAndYear(portfolioId, year).isPresent())
      return YearReviewMode.CLOSED;
    return YearReviewMode.NONE;
  }

  public YearReview yearReview(PastPlanningYear year) {
    return planningYearReviews.review(year);
  }

  @Transactional
  public PastPlanningYear createHistoricalDraft(Long portfolioId, int year) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException(
          "Historical planning year must be before the current year");
    RetirementPlanningYearEntity planningYear = year(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) return past(planningYear);
    if (records(planningYear, PlanningValueKind.ACTUAL).isEmpty()) {
      Map<PlanningMetric, PlanningMetricValue> derived = new EnumMap<>(PlanningMetric.class);
      derived.putAll(metrics.historicalMarket(portfolioId, year));
      derived.putAll(metrics.historicalLongTermAssets(portfolioId, year));
      for (PlanningMetric metric :
          List.of(
              PlanningMetric.NET_WORTH,
              PlanningMetric.REAL_ESTATE,
              PlanningMetric.BOND_VALUE,
              PlanningMetric.BOND_INCOME,
              PlanningMetric.CASH_RESERVE_VALUE,
              PlanningMetric.CORE_SPENDING,
              PlanningMetric.DISCRETIONARY_SPENDING)) {
        derived.putIfAbsent(metric, unavailable(metric));
      }
      derived.forEach((metric, value) -> upsert(planningYear, PlanningValueKind.ACTUAL, value));
    }
    return past(planningYear);
  }

  /**
   * Adds a retrospective Planned reference to a historical draft when the selected plan starts in
   * that year or earlier. This does not reconstruct historical facts: it freezes what the selected
   * current plan says for that calendar year so the reviewer can compare Planned vs Actual.
   */
  @Transactional
  public PastPlanningYear seedHistoricalBaselineFromPlan(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException(
          "Historical planning year must be before the current year");
    if (year < assumptions.planStartYear()) return createHistoricalDraft(portfolioId, year);

    RetirementPlanningYearEntity planningYear = year(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) return past(planningYear);
    createHistoricalDraft(portfolioId, year);

    if (!records(planningYear, PlanningValueKind.BASELINE).isEmpty()) return past(planningYear);

    SimulationYear expected =
        simulations
            .simulate(profile, assumptionsForYear(assumptions, year), SimulationScenario.BASE, year)
            .years()
            .getFirst();

    Map<PlanningMetric, BigDecimal> planned = new EnumMap<>(expectedValues(expected));
    SimulationAssumptions historicalAssumptions = assumptionsForYear(assumptions, year);
    // Simulation rows report spending only after retirement. A retrospective Plan must still
    // show the selected plan's spending assumptions for working years.
    planned.put(PlanningMetric.CORE_SPENDING, historicalAssumptions.annualLivingExpenses());
    planned.put(
        PlanningMetric.DISCRETIONARY_SPENDING, historicalAssumptions.annualDiscretionaryExpenses());
    // A retrospective plan has no contemporaneous market snapshot. Use the selected current
    // plan/profile value as the explicit Planned reference for this historical comparison.
    planned.putIfAbsent(PlanningMetric.MARKET_ASSETS, profile.marketPortfolioValue());

    planned.forEach(
        (metric, amount) ->
            upsert(
                planningYear,
                PlanningValueKind.BASELINE,
                new PlanningMetricValue(
                    metric,
                    amount,
                    null,
                    PlanningValueSource.SIMULATION_BASELINE,
                    "Retrospective reference from selected plan")));

    planningYear.setBaselinePlanId(planId);
    planningYear.setBaselineCreatedAt(Instant.now(clock));
    saveState(planningYear);
    return past(planningYear);
  }

  /** Refreshes accounting- and supported Long-term AssetEntity-derived metrics on an open year. */
  @Transactional
  public PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year) {
    RetirementPlanningYearEntity planningYear = year(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED)
      throw new IllegalStateException("Closed planning year cannot refresh accounting values");
    HistoricalPortfolioYear source = metrics.historicalPortfolio(portfolioId, year);
    Map<PlanningMetric, PlanningMetricValue> refreshed = new EnumMap<>(PlanningMetric.class);
    refreshed.putAll(
        source.complete()
            ? metrics.historicalMarket(portfolioId, year)
            : List.of(
                    PlanningMetric.MARKET_ASSETS,
                    PlanningMetric.MARKET_INCOME,
                    PlanningMetric.MARKET_WITHDRAWAL,
                    PlanningMetric.MARKET_RETURN)
                .stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        metric -> metric,
                        metric -> unavailable(metric),
                        (left, right) -> left,
                        () -> new EnumMap<>(PlanningMetric.class))));
    refreshed.putAll(metrics.historicalLongTermAssets(portfolioId, year));
    refreshed.forEach(
        (metric, derived) -> {
          PlanningMetricValue existing =
              records(planningYear, PlanningValueKind.ACTUAL).get(metric);
          if (existing != null
              && (existing.source() == PlanningValueSource.USER_OVERRIDE
                  || existing.source() == PlanningValueSource.USER_ENTERED)) return;
          upsert(planningYear, PlanningValueKind.ACTUAL, derived);
        });
    return past(planningYear);
  }

  /** Ensures the calendar-current planning year exists without changing historical state. */
  @Transactional
  public boolean ensureCurrentYear(Long portfolioId) {
    int current = activeCurrentYear(portfolioId);
    if (years.findByPortfolioIdAndYear(portfolioId, current).isPresent()) return false;
    year(portfolioId, current);
    return true;
  }

  /** Returns planning years before the calendar-current year, regardless of close status. */
  @Transactional(readOnly = true)
  public List<Integer> historicalYears(Long portfolioId) {
    int current = activeCurrentYear(portfolioId);
    return years.findAllByPortfolioIdAndYearLessThanOrderByYearAsc(portfolioId, current).stream()
        .map(RetirementPlanningYearEntity::getYear)
        .toList();
  }

  @Transactional
  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    requireCurrent(portfolioId, year);
    RetirementPlanningYearEntity planningYear = year(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED)
      throw new IllegalStateException("Closed planning year cannot refresh baseline");
    SimulationYear expected =
        simulations
            .simulate(profile, assumptionsForYear(assumptions, year), SimulationScenario.BASE, year)
            .years()
            .getFirst();
    Map<PlanningMetric, BigDecimal> expectedValues = expectedValues(expected);
    expectedValues.forEach(
        (metric, amount) ->
            upsert(
                planningYear,
                PlanningValueKind.BASELINE,
                new PlanningMetricValue(
                    metric, amount, null, PlanningValueSource.SIMULATION_BASELINE, null)));
    planningYear.setBaselinePlanId(planId);
    planningYear.setBaselineCreatedAt(Instant.now(clock));
    saveState(planningYear);
  }

  @Transactional
  public void saveCurrentManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal approvedValue, String note) {
    requireCurrent(portfolioId, year);
    if (metric == PlanningMetric.NET_WORTH)
      throw new IllegalArgumentException("Live net worth is derived from authoritative facts");
    if (metric == PlanningMetric.REAL_ESTATE)
      throw new IllegalArgumentException("Live real-estate value is derived from the portfolio");
    saveDraftManualValue(portfolioId, year, metric, approvedValue, note);
  }

  @Transactional
  public void saveDraftManualValue(
      Long portfolioId, int year, PlanningMetric metric, BigDecimal approvedValue, String note) {
    RetirementPlanningYearEntity planningYear = year(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED)
      throw new IllegalStateException("Closed planning year cannot be edited");
    PlanningMetricValue current = records(planningYear, PlanningValueKind.ACTUAL).get(metric);
    validateManualEdit(metric, current);
    upsert(
        planningYear,
        PlanningValueKind.ACTUAL,
        new PlanningMetricValue(
            metric,
            current == null ? null : current.derivedValue(),
            approvedValue,
            PlanningValueSource.USER_OVERRIDE,
            note));
  }

  @Transactional
  public PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile) {
    if (year >= calendarCurrentYear())
      throw new IllegalArgumentException(
          "A planning year can be closed only after its calendar year ends");
    RetirementPlanningYearEntity planningYear = year(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) {
      ensureClosedTimestamp(planningYear);
      return past(planningYear);
    }
    Map<PlanningMetric, PlanningMetricValue> live = metrics.currentActual(profile);
    live.forEach(
        (metric, value) -> {
          PlanningMetricValue stored = records(planningYear, PlanningValueKind.ACTUAL).get(metric);
          if (stored == null) upsert(planningYear, PlanningValueKind.ACTUAL, value);
        });
    ensureComplete(planningYear);
    planningYear.setStatus(PlanningYearStatus.CLOSED);
    planningYear.setClosedAt(Instant.now(clock));
    saveState(planningYear);
    return past(planningYear);
  }

  @Transactional
  public void reopenHistoricalYear(Long portfolioId, int year) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException("Only a historical planning year can be reopened");
    RetirementPlanningYearEntity planningYear = get(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() != PlanningYearStatus.CLOSED) return;
    planningYear.setStatus(PlanningYearStatus.DRAFT);
    planningYear.setReopenedAt(Instant.now(clock));
    saveState(planningYear);
  }

  @Transactional
  public PastPlanningYear closeHistoricalDraft(Long portfolioId, int year) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException("Only a past draft can be closed here");
    RetirementPlanningYearEntity planningYear = get(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.DRAFT) {
      ensureComplete(planningYear);
      planningYear.setStatus(PlanningYearStatus.CLOSED);
      planningYear.setClosedAt(Instant.now(clock));
      saveState(planningYear);
    } else {
      ensureClosedTimestamp(planningYear);
    }
    return past(planningYear);
  }

  /** Uses the request's already-prepared forward boundary and does not bridge it again. */
  @Transactional(readOnly = true)
  public PlanningTimeline loadForwardTimeline(
      Long portfolioId, RetirementProjection projection, SimulationScenario scenario) {
    InvestmentProfile profile = projection.profile();
    ForwardSimulationInput forward = projection.forward();
    int current = activeCurrentYear(portfolioId);
    List<PlanningTimelineYear> result = new ArrayList<>();
    int planStartYear = forward.context().originalStartYear();
    for (int year = planStartYear; year < current; year++) {
      RetirementPlanningYearEntity stored =
          years.findByPortfolioIdAndYear(portfolioId, year).orElse(null);
      if (stored != null) hydrate(stored);
      result.add(
          new PlanningTimelineYear(
              year,
              forward.context().originalCurrentAge() + year - forward.context().originalStartYear(),
              stored == null ? PlanningTimelineState.NEEDS_REVIEW : state(stored),
              stored == null ? null : past(stored),
              null,
              null));
    }
    result.add(
        new PlanningTimelineYear(
            current,
            forward.context().asOfAge(),
            PlanningTimelineState.LIVE,
            null,
            currentForTimeline(
                portfolioId,
                current,
                profile,
                forward.context().originalAssumptions(),
                forward.currentYearBridge()),
            null));
    SimulationResult projectedScenario = projection.scenarioResults().get(scenario);
    if (projectedScenario != null)
      for (SimulationYear projectedYear : projectedScenario.years())
        result.add(
            new PlanningTimelineYear(
                projectedYear.year(),
                projectedYear.age(),
                PlanningTimelineState.PROJECTED,
                null,
                null,
                projectedYear));
    return new PlanningTimeline(profile.currency(), result);
  }

  /**
   * Explicitly creates and derives missing historical years without changing closed/manual data.
   */
  @Transactional
  public List<Integer> prefillHistoricalYears(Long portfolioId, int planStartYear) {
    int current = activeCurrentYear(portfolioId);
    if (planStartYear > current)
      throw new IllegalArgumentException("Plan start year cannot be in the future");
    List<Integer> populated = new ArrayList<>();
    for (int year = planStartYear; year < current; year++) {
      createHistoricalDraft(portfolioId, year);
      populated.add(year);
    }
    return List.copyOf(populated);
  }

  @Transactional(readOnly = true)
  public CurrentPlanningYear current(Long portfolioId, int year, InvestmentProfile profile) {
    RetirementPlanningYearEntity planningYear =
        years.findByPortfolioIdAndYear(portfolioId, year).orElse(null);
    if (planningYear != null) hydrate(planningYear);
    Map<PlanningMetric, PlanningMetricValue> live = metrics.currentActual(profile);
    if (planningYear == null) return new CurrentPlanningYear(year, null, null, live, Map.of());
    Map<PlanningMetric, PlanningMetricValue> manual = actualValues(planningYear);
    manual.forEach(
        (metric, value) -> {
          if (value.approvedValue() != null) live.put(metric, value);
        });
    return new CurrentPlanningYear(
        year,
        planningYear.getBaselinePlanId(),
        planningYear.getBaselineCreatedAt(),
        live,
        planningYear.getBaselinePlanId() == null ? Map.of() : baselineValues(planningYear));
  }

  @Transactional(readOnly = true)
  public PastPlanningYear pastYear(Long portfolioId, int year) {
    return past(get(portfolioId, year));
  }

  /** UI hint only; saveDraftManualValue remains the authoritative enforcement point. */
  @Transactional(readOnly = true)
  public boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric) {
    RetirementPlanningYearEntity planningYear = get(portfolioId, year);
    hydrate(planningYear);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) return false;
    PlanningMetricValue current = records(planningYear, PlanningValueKind.ACTUAL).get(metric);
    return isManualEditAllowed(metric, current);
  }

  @Transactional(readOnly = true)
  public PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year) {
    return closeStatus(get(portfolioId, year));
  }

  private SimulationAssumptions assumptionsForYear(SimulationAssumptions assumptions, int year) {
    int offset = year - assumptions.startYear();
    return assumptions.toBuilder()
        .currentAge(assumptions.currentAge() + offset)
        .annualLivingExpenses(
            PlanningTimelineValueSupport.growForYears(
                assumptions.annualLivingExpenses(),
                assumptions.effectiveSpendingGrowthRate(),
                offset))
        .startYear(year)
        .annualDiscretionaryExpenses(
            PlanningTimelineValueSupport.growForYears(
                assumptions.annualDiscretionaryExpenses(),
                assumptions.effectiveSpendingGrowthRate(),
                offset))
        .expenseProfile(assumptions.expenseProfile().rebasedAt(offset))
        .build();
  }

  private static Map<PlanningMetric, BigDecimal> expectedValues(SimulationYear row) {
    Map<PlanningMetric, BigDecimal> values = new EnumMap<>(PlanningMetric.class);
    values.put(PlanningMetric.NET_WORTH, row.endNetWorth());
    values.put(PlanningMetric.SAFE_RESERVE, row.safeReserveEnd());
    values.put(PlanningMetric.SAFE_RESERVE_TARGET, row.safeReserveTarget());
    values.put(PlanningMetric.MANUAL_LIQUID_RESERVE, row.manualLiquidReserveEnd());
    values.put(PlanningMetric.FIXED_INCOME, row.fixedIncomeEnd());
    values.put(PlanningMetric.EQUITY, row.equityEnd());
    values.put(PlanningMetric.REAL_ESTATE, row.realEstateEnd());
    values.put(PlanningMetric.RENTAL_INCOME, row.rentalIncome());
    values.put(PlanningMetric.BOND_INCOME, row.bondIncome());
    values.put(PlanningMetric.BOND_VALUE, row.bondValueEnd());
    values.put(PlanningMetric.CORE_SPENDING, row.coreExpenses());
    values.put(PlanningMetric.DISCRETIONARY_SPENDING, row.discretionaryExpenses());
    values.put(PlanningMetric.PORTFOLIO_FUNDING, row.requiredPortfolioFunding());
    values.put(PlanningMetric.EQUITY_RETURN, row.equityReturnRate());
    values.put(PlanningMetric.EQUITY_HARVEST, row.equityToFixedIncomeTransfer());
    values.put(PlanningMetric.EMERGENCY_EQUITY_WITHDRAWAL, row.emergencyEquityWithdrawal());
    return values;
  }

  private PastPlanningYear past(RetirementPlanningYearEntity year) {
    return new PastPlanningYear(
        year.getYear(),
        year.getStatus(),
        year.getClosedAt(),
        year.getBaselinePlanId(),
        actualValues(year),
        baselineValues(year));
  }

  private Map<PlanningMetric, PlanningMetricValue> actualValues(RetirementPlanningYearEntity year) {
    return records(year, PlanningValueKind.ACTUAL);
  }

  private Map<PlanningMetric, PlanningMetricValue> baselineValues(
      RetirementPlanningYearEntity year) {
    return records(year, PlanningValueKind.BASELINE);
  }

  private Map<PlanningMetric, PlanningMetricValue> records(
      RetirementPlanningYearEntity year, PlanningValueKind kind) {
    return year.getValues().get(kind);
  }

  private RetirementPlanningYearEntity year(Long portfolioId, int calendarYear) {
    RetirementPlanningYearEntity result =
        years
            .findByPortfolioIdAndYear(portfolioId, calendarYear)
            .orElseGet(
                () -> {
                  RetirementPlanningYearEntity created = new RetirementPlanningYearEntity();
                  created.setPortfolioId(portfolioId);
                  created.setYear(calendarYear);
                  created.setStatus(PlanningYearStatus.DRAFT);
                  return years.save(created);
                });
    hydrate(result);
    return result;
  }

  private RetirementPlanningYearEntity get(Long portfolioId, int calendarYear) {
    RetirementPlanningYearEntity result =
        years
            .findByPortfolioIdAndYear(portfolioId, calendarYear)
            .orElseThrow(() -> new NoSuchElementException("Planning year not found"));
    hydrate(result);
    return result;
  }

  private void upsert(
      RetirementPlanningYearEntity year, PlanningValueKind kind, PlanningMetricValue value) {
    year.getValues().get(kind).put(value.metric(), value);
    saveState(year);
  }

  private void hydrate(RetirementPlanningYearEntity year) {
    stateCodec.readInto(year);
  }

  private RetirementPlanningYearEntity saveState(RetirementPlanningYearEntity year) {
    stateCodec.writeFrom(year);
    return years.save(year);
  }

  private static PlanningMetricValue derived(
      PlanningMetric metric, BigDecimal amount, PlanningValueSource source) {
    return new PlanningMetricValue(metric, amount, null, source, null);
  }

  private static PlanningMetricValue unavailable(PlanningMetric metric) {
    return PlanningTimelineValueSupport.unavailable(metric);
  }

  private CurrentPlanningYear currentForTimeline(
      Long portfolioId,
      int year,
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      CurrentYearProjection bridge) {
    CurrentPlanningYear current = current(portfolioId, year, profile);
    Map<PlanningMetric, PlanningMetricValue> live = new EnumMap<>(current.actualValues());
    live.put(
        PlanningMetric.CORE_SPENDING,
        derived(
            PlanningMetric.CORE_SPENDING,
            assumptions.annualLivingExpenses(),
            PlanningValueSource.SIMULATION_BASELINE));
    live.put(
        PlanningMetric.DISCRETIONARY_SPENDING,
        derived(
            PlanningMetric.DISCRETIONARY_SPENDING,
            assumptions.annualDiscretionaryExpenses(),
            PlanningValueSource.SIMULATION_BASELINE));
    live.put(
        PlanningMetric.CASH_RESERVE_VALUE,
        derived(
            PlanningMetric.CASH_RESERVE_VALUE,
            profile.retirementReserve(),
            PlanningValueSource.PORTFOLIO_DERIVED));
    if (currentLongTermAssets != null) {
      LongTermAssetAnnualSnapshotModel facts =
          currentLongTermAssets.snapshot(portfolioId, LocalDate.now(clock)).annualSnapshot();
      putCurrentFact(
          live, PlanningMetric.RENTAL_INCOME, canonical(facts.rentalIncome(), facts.currency()));
      putCurrentFact(
          live, PlanningMetric.BOND_VALUE, canonical(facts.bondValue(), facts.currency()));
      putCurrentFact(
          live, PlanningMetric.BOND_INCOME, canonical(facts.bondIncome(), facts.currency()));
    }
    if (bridge != null) {
      live.put(
          PlanningMetric.PORTFOLIO_FUNDING,
          derived(
              PlanningMetric.PORTFOLIO_FUNDING,
              bridge.requiredPortfolioFunding(),
              PlanningValueSource.SIMULATION_BASELINE));
    }
    BigDecimal annualizedSpending =
        assumptions
            .annualLivingExpenses()
            .add(assumptions.annualDiscretionaryExpenses())
            .add(eventAmount(assumptions, year, SimulationEventType.ONE_OFF_EXPENSE));
    Map<PlanningMetric, PlanningMetricValue> expected = new EnumMap<>(PlanningMetric.class);
    expected.putAll(current.expectedValues());
    if (bridge != null) {
      putExpectedBucket(
          expected,
          PlanningMetric.CASH_RESERVE_VALUE,
          bridge.expectedEnd(EconomicBucket.LIQUID_CASH));
      putExpectedBucket(
          expected, PlanningMetric.SAFE_RESERVE, bridge.expectedEnd(EconomicBucket.LIQUID_CASH));
      putExpectedBucket(
          expected, PlanningMetric.FIXED_INCOME, bridge.expectedEnd(EconomicBucket.FIXED_INCOME));
      putExpectedBucket(
          expected, PlanningMetric.BOND_VALUE, bridge.expectedEnd(EconomicBucket.FIXED_INCOME));
      putExpectedBucket(expected, PlanningMetric.EQUITY, bridge.expectedEnd(EconomicBucket.EQUITY));
      putExpectedBucket(
          expected, PlanningMetric.REAL_ESTATE, bridge.expectedEnd(EconomicBucket.REAL_ESTATE));
      putExpectedBucket(
          expected, PlanningMetric.NET_WORTH, bridge.bridgedProfile().totalNetWorth());
    }
    return new CurrentPlanningYear(
        current.year(),
        current.baselinePlanId(),
        current.baselineCreatedAt(),
        live,
        expected,
        annualizedSpending);
  }

  private static BigDecimal eventAmount(
      SimulationAssumptions assumptions, int year, SimulationEventType type) {
    return assumptions.futureEvents().stream()
        .filter(event -> event.year() == year && event.type() == type)
        .map(SimulationEvent::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static void putExpectedBucket(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric, BigDecimal amount) {
    if (amount != null)
      values.put(metric, derived(metric, amount, PlanningValueSource.SIMULATION_BASELINE));
  }

  private static void putCurrentFact(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric, BigDecimal amount) {
    if (amount != null)
      values.put(metric, derived(metric, amount, PlanningValueSource.LONG_TERM_DERIVED));
  }

  private BigDecimal canonical(BigDecimal amount, CurrencyType source) {
    return source == FinancialPolicyDefaults.CANONICAL_CURRENCY
        ? amount
        : money.toCanonical(amount, source);
  }

  private int calendarCurrentYear() {
    return Year.now(clock).getValue();
  }

  private int activeCurrentYear(Long portfolioId) {
    return calendarCurrentYear();
  }

  private void requireCurrent(Long portfolioId, int year) {
    if (year != activeCurrentYear(portfolioId))
      throw new IllegalArgumentException("Only the current planning year is live");
  }

  private void validateManualEdit(PlanningMetric metric, PlanningMetricValue current) {
    if (!isManualEditAllowed(metric, current)) {
      if (metric == PlanningMetric.REAL_ESTATE
          && current != null
          && current.derivedValue() != null
          && current.source() != PlanningValueSource.UNAVAILABLE) {
        throw new IllegalArgumentException("Derived real-estate value cannot be overridden");
      }
      throw new IllegalArgumentException(
          metric + " is derived from authoritative portfolio data and cannot be overridden");
    }
  }

  private static boolean isManualEditAllowed(PlanningMetric metric, PlanningMetricValue current) {
    if (!metric.isManualEditable()) return false;
    return metric != PlanningMetric.REAL_ESTATE
        || current == null
        || current.derivedValue() == null
        || current.source() == PlanningValueSource.UNAVAILABLE;
  }

  private void ensureComplete(RetirementPlanningYearEntity planningYear) {
    PlanningYearCloseStatus status = closeStatus(planningYear);
    if (!status.canClose())
      throw new IllegalStateException(
          "Cannot close "
              + planningYear.getYear()
              + ". Missing: "
              + String.join(", ", status.missingMetrics()));
  }

  private static PlanningTimelineState state(RetirementPlanningYearEntity year) {
    return year.getStatus() == PlanningYearStatus.CLOSED
        ? PlanningTimelineState.ACTUAL
        : PlanningTimelineState.NEEDS_REVIEW;
  }

  private void ensureClosedTimestamp(RetirementPlanningYearEntity planningYear) {
    if (planningYear.getClosedAt() == null) {
      planningYear.setClosedAt(Instant.now(clock));
      saveState(planningYear);
    }
  }

  private PlanningYearCloseStatus closeStatus(RetirementPlanningYearEntity planningYear) {
    Map<PlanningMetric, PlanningMetricValue> actual = actualValues(planningYear);
    List<String> missing = new ArrayList<>();
    if (value(actual, PlanningMetric.NET_WORTH) == null
        && value(actual, PlanningMetric.MARKET_ASSETS) == null)
      missing.add("Net worth or market assets");
    for (PlanningMetric metric : PlanningMetric.values())
      if (metric.isRequiredForClose() && value(actual, metric) == null) missing.add(metric.label());
    return new PlanningYearCloseStatus(missing.isEmpty(), List.copyOf(missing));
  }

  private static BigDecimal value(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values.get(metric);
    return value == null ? null : value.value();
  }
}
