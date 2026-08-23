package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.investment.api.HistoricalPortfolioActualsReader;
import com.smartbox.investory.investment.api.HistoricalPortfolioYear;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.longterm.api.model.LongTermAssetTypeModel;
import com.smartbox.investory.retirement.infrastructure.planning.*;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.retirement.simulation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write planning layer. It reads accounting/portfolio data but never writes it. Closed
 * planning values are copied here and never recomputed by later simulation changes.
 */
@Service
public class PlanningTimelineFacade {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final PlanningYearRepository years;
  private final PlanningYearValueRepository values;
  private final HistoricalPortfolioActualsReader historicalPortfolio;
  private final HistoricalLongTermAssetYearSource historicalLongTermAssets;
  private final RetirementSimulation simulations;
  private final CurrentYearProjectionBridge projectionBridge;
  private final Clock clock;
  private final ForwardSimulationContextFactory forwardContexts;
  private final LongTermAssetAnnualSnapshotReader longTermAssets;
  private final PlanningProgressService planningProgress = new PlanningProgressService();
  private final YearReviewService yearReviews = new YearReviewService(planningProgress);

  @Autowired
  public PlanningTimelineFacade(
      PlanningYearRepository years,
      PlanningYearValueRepository values,
      HistoricalPortfolioActualsReader historicalPortfolio,
      HistoricalLongTermAssetYearSource historicalLongTermAssets,
      RetirementSimulation simulations,
      CurrentYearProjectionBridge projectionBridge,
      Clock clock,
      ForwardSimulationContextFactory forwardContexts,
      LongTermAssetAnnualSnapshotReader longTermAssets) {
    this.years = years;
    this.values = values;
    this.historicalPortfolio = historicalPortfolio;
    this.historicalLongTermAssets = historicalLongTermAssets;
    this.simulations = simulations;
    this.projectionBridge = projectionBridge;
    this.clock = clock;
    this.forwardContexts = forwardContexts;
    this.longTermAssets = longTermAssets;
  }

  /** Application read facade for planning progress and year review composition. */
  public PlanProgress progress(PlanningTimeline timeline) {
    return planningProgress.progressForTimeline(timeline);
  }

  public YearReview yearReview(PastPlanningYear year) {
    return yearReviews.review(year);
  }

  public PlanningTimelineFacade(
      PlanningYearRepository years,
      PlanningYearValueRepository values,
      HistoricalPortfolioActualsReader historicalPortfolio,
      HistoricalLongTermAssetYearSource historicalLongTermAssets,
      RetirementSimulation simulations,
      CurrentYearProjectionBridge projectionBridge,
      Clock clock,
      ForwardSimulationContextFactory forwardContexts) {
    this(
        years,
        values,
        historicalPortfolio,
        historicalLongTermAssets,
        simulations,
        projectionBridge,
        clock,
        forwardContexts,
        null);
  }

  @Transactional
  public PastPlanningYear createHistoricalDraft(Long portfolioId, int year) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException(
          "Historical planning year must be before the current year");
    PlanningYearEntity planningYear = year(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) return past(planningYear);
    if (values
        .findAllByPlanningYearIdAndValueKind(planningYear.getId(), PlanningValueKind.ACTUAL)
        .isEmpty()) {
      Map<PlanningMetric, PlanningMetricValue> derived = new EnumMap<>(PlanningMetric.class);
      derived.putAll(deriveHistoricalMarket(portfolioId, year));
      derived.putAll(deriveHistoricalLongTermAssets(portfolioId, year));
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
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException(
          "Historical planning year must be before the current year");
    if (year < assumptions.planStartYear()) return createHistoricalDraft(portfolioId, year);

    PlanningYearEntity planningYear = year(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) return past(planningYear);
    createHistoricalDraft(portfolioId, year);

    if (!values
        .findAllByPlanningYearIdAndValueKind(planningYear.getId(), PlanningValueKind.BASELINE)
        .isEmpty()) return past(planningYear);

    SimulationYear expected =
        simulations
            .simulate(
                profile,
                assumptionsForYear(assumptions, year),
                SimulationScenario.BASE,
                year)
            .years()
            .getFirst();

    Map<PlanningMetric, BigDecimal> planned = new EnumMap<>(expectedValues(expected));
    SimulationAssumptions historicalAssumptions = assumptionsForYear(assumptions, year);
    // Simulation rows report spending only after retirement. A retrospective Plan must still
    // show the selected plan's spending assumptions for working years.
    planned.put(PlanningMetric.CORE_SPENDING, historicalAssumptions.annualLivingExpenses());
    planned.put(
        PlanningMetric.DISCRETIONARY_SPENDING,
        historicalAssumptions.annualDiscretionaryExpenses());
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
    planningYear.setBaselineRevisionId(revisionId);
    planningYear.setBaselineCreatedAt(Instant.now(clock));
    years.save(planningYear);
    return past(planningYear);
  }

  /** Refreshes accounting- and supported Long-term AssetEntity-derived metrics on an open year. */
  @Transactional
  public PastPlanningYear refreshHistoricalDerivedValues(Long portfolioId, int year) {
    PlanningYearEntity planningYear = year(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED)
      throw new IllegalStateException("Closed planning year cannot refresh accounting values");
    HistoricalPortfolioYear source = historicalPortfolio.read(portfolioId, year);
    Map<PlanningMetric, PlanningMetricValue> refreshed = new EnumMap<>(PlanningMetric.class);
    refreshed.putAll(
        source.complete()
            ? deriveHistoricalMarket(portfolioId, year)
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
    refreshed.putAll(deriveHistoricalLongTermAssets(portfolioId, year));
    refreshed.forEach(
        (metric, derived) -> {
          PlanningYearValueEntity existing =
              values
                  .findByPlanningYearIdAndValueKindAndMetric(
                      planningYear.getId(), PlanningValueKind.ACTUAL, metric)
                  .orElse(null);
          if (existing != null
              && (existing.getSourceType() == PlanningValueSource.USER_OVERRIDE
                  || existing.getSourceType() == PlanningValueSource.USER_ENTERED)) return;
          upsert(planningYear, PlanningValueKind.ACTUAL, derived);
        });
    return past(planningYear);
  }

  /** Compatibility alias for the previous accounting-only refresh command. */
  @Transactional
  public PastPlanningYear refreshHistoricalAccounting(Long portfolioId, int year) {
    return refreshHistoricalDerivedValues(portfolioId, year);
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
    return years.findAllByPortfolioIdOrderByYearAsc(portfolioId).stream()
        .map(PlanningYearEntity::getYear)
        .filter(year -> year < current)
        .toList();
  }

  @Transactional
  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    setCurrentBaseline(portfolioId, year, planId, null, profile, assumptions);
  }

  @Transactional
  public void setCurrentBaseline(
      Long portfolioId,
      int year,
      Long planId,
      Long revisionId,
      InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    requireCurrent(portfolioId, year);
    PlanningYearEntity planningYear = year(portfolioId, year);
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
    planningYear.setBaselineRevisionId(revisionId);
    planningYear.setBaselineCreatedAt(Instant.now(clock));
    years.save(planningYear);
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
    PlanningYearEntity planningYear = year(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED)
      throw new IllegalStateException("Closed planning year cannot be edited");
    PlanningYearValueEntity current =
        values
            .findByPlanningYearIdAndValueKindAndMetric(
                planningYear.getId(), PlanningValueKind.ACTUAL, metric)
            .orElseGet(PlanningYearValueEntity::new);
    validateManualEdit(metric, current);
    current.setPlanningYearId(planningYear.getId());
    current.setValueKind(PlanningValueKind.ACTUAL);
    current.setMetric(metric);
    current.setDerivedValue(current.getDerivedValue());
    current.setApprovedValue(approvedValue);
    current.setSourceType(PlanningValueSource.USER_OVERRIDE);
    current.setNote(note);
    values.save(current);
  }

  @Transactional
  public PastPlanningYear closeCurrentYear(Long portfolioId, int year, InvestmentProfile profile) {
    if (year >= calendarCurrentYear())
      throw new IllegalArgumentException(
          "A planning year can be closed only after its calendar year ends");
    PlanningYearEntity planningYear = year(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) {
      ensureClosedTimestamp(planningYear);
      return past(planningYear);
    }
    Map<PlanningMetric, PlanningMetricValue> live = currentActual(profile);
    live.forEach(
        (metric, value) -> {
          PlanningYearValueEntity stored =
              values
                  .findByPlanningYearIdAndValueKindAndMetric(
                      planningYear.getId(), PlanningValueKind.ACTUAL, metric)
                  .orElse(null);
          if (stored == null) upsert(planningYear, PlanningValueKind.ACTUAL, value);
        });
    ensureComplete(planningYear);
    planningYear.setStatus(PlanningYearStatus.CLOSED);
    planningYear.setClosedAt(Instant.now(clock));
    years.save(planningYear);
    return past(planningYear);
  }

  @Transactional
  public void reopenHistoricalYear(Long portfolioId, int year) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException("Only a historical planning year can be reopened");
    PlanningYearEntity planningYear = get(portfolioId, year);
    if (planningYear.getStatus() != PlanningYearStatus.CLOSED) return;
    planningYear.setStatus(PlanningYearStatus.DRAFT);
    planningYear.setReopenedAt(Instant.now(clock));
    years.save(planningYear);
  }

  /** Compatibility alias for the historical correction endpoint. */
  @Deprecated
  @Transactional
  public void reopen(Long portfolioId, int year) {
    reopenHistoricalYear(portfolioId, year);
  }

  @Transactional
  public PastPlanningYear closeHistoricalDraft(Long portfolioId, int year) {
    if (year >= activeCurrentYear(portfolioId))
      throw new IllegalArgumentException("Only a past draft can be closed here");
    PlanningYearEntity planningYear = get(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.DRAFT) {
      ensureComplete(planningYear);
      planningYear.setStatus(PlanningYearStatus.CLOSED);
      planningYear.setClosedAt(Instant.now(clock));
      years.save(planningYear);
    } else {
      ensureClosedTimestamp(planningYear);
    }
    return past(planningYear);
  }

  @Transactional(readOnly = true)
  @Deprecated
  public PlanningTimeline loadTimeline(
      Long portfolioId, InvestmentProfile profile, SimulationAssumptions assumptions) {
    int current = activeCurrentYear(portfolioId);
    List<PlanningTimelineYear> result = new ArrayList<>();
    years.findAllByPortfolioIdOrderByYearAsc(portfolioId).stream()
        .filter(year -> year.getYear() < current)
        .forEach(
            year ->
                result.add(
                    new PlanningTimelineYear(
                        year.getYear(),
                        age(assumptions, year.getYear()),
                        state(year),
                        past(year),
                        null,
                        null)));
    result.add(
        new PlanningTimelineYear(
            current,
            age(assumptions, current),
            PlanningTimelineState.LIVE,
            null,
            currentForTimeline(portfolioId, current, profile, assumptions, null),
            null));
    for (SimulationYear projection : future(profile, assumptions, current)) {
      result.add(
          new PlanningTimelineYear(
              projection.year(),
              projection.age(),
              PlanningTimelineState.PROJECTED,
              null,
              null,
              projection));
    }
    return new PlanningTimeline(result);
  }

  /** Uses the request's already-prepared forward boundary and does not bridge it again. */
  @Transactional(readOnly = true)
  public PlanningTimeline loadForwardTimeline(
      Long portfolioId, InvestmentProfile profile, ForwardSimulationInput forward) {
    return loadForwardTimeline(portfolioId, profile, forward, SimulationScenario.BASE);
  }

  @Transactional(readOnly = true)
  public PlanningTimeline loadForwardTimeline(
      Long portfolioId,
      InvestmentProfile profile,
      ForwardSimulationInput forward,
      SimulationScenario scenario) {
    int current = activeCurrentYear(portfolioId);
    List<PlanningTimelineYear> result = new ArrayList<>();
    int planStartYear = forward.context().originalStartYear();
    for (int year = planStartYear; year < current; year++) {
      PlanningYearEntity stored =
          years.findByPortfolioIdAndYear(portfolioId, year).orElse(null);
      result.add(
          new PlanningTimelineYear(
              year,
              forward.context().originalCurrentAge()
                  + year
                  - forward.context().originalStartYear(),
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
                portfolioId, current, profile, forward.context().originalAssumptions(),
                forward.currentYearBridge()),
            null));
    if (forward.forwardAssumptions().isPresent())
      for (SimulationYear projection : future(forward, current, scenario))
        result.add(
            new PlanningTimelineYear(
                projection.year(),
                projection.age(),
                PlanningTimelineState.PROJECTED,
                null,
                null,
                projection));
    return new PlanningTimeline(result);
  }

  /** Explicitly creates and derives missing historical years without changing closed/manual data. */
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
    PlanningYearEntity planningYear =
        years.findByPortfolioIdAndYear(portfolioId, year).orElse(null);
    Map<PlanningMetric, PlanningMetricValue> live = currentActual(profile);
    if (planningYear == null) return new CurrentPlanningYear(year, null, null, live, Map.of());
    Map<PlanningMetric, PlanningMetricValue> manual = actualValues(planningYear);
    manual.forEach(
        (metric, value) -> {
          if (value.approvedValue() != null) live.put(metric, value);
        });
    return new CurrentPlanningYear(
        year,
        planningYear.getBaselinePlanId(),
        planningYear.getBaselineRevisionId(),
        planningYear.getBaselineCreatedAt(),
        live,
        planningYear.getBaselinePlanId() == null || planningYear.getBaselineRevisionId() == null
            ? Map.of()
            : baselineValues(planningYear));
  }

  @Transactional(readOnly = true)
  public PastPlanningYear pastYear(Long portfolioId, int year) {
    return past(get(portfolioId, year));
  }

  /** UI hint only; saveDraftManualValue remains the authoritative enforcement point. */
  @Transactional(readOnly = true)
  public boolean isHistoricalMetricEditable(Long portfolioId, int year, PlanningMetric metric) {
    PlanningYearEntity planningYear = get(portfolioId, year);
    if (planningYear.getStatus() == PlanningYearStatus.CLOSED) return false;
    PlanningYearValueEntity current =
        values
            .findByPlanningYearIdAndValueKindAndMetric(
                planningYear.getId(), PlanningValueKind.ACTUAL, metric)
            .orElse(null);
    return isManualEditAllowed(metric, current);
  }

  @Transactional(readOnly = true)
  public PlanningYearCloseStatus historicalCloseStatus(Long portfolioId, int year) {
    return closeStatus(get(portfolioId, year));
  }

  private List<SimulationYear> future(
      InvestmentProfile profile, SimulationAssumptions assumptions, int current) {
    ForwardSimulationContext context = forwardContexts.create(profile, assumptions);
    if (context.forwardAssumptions().isEmpty()) return List.of();
    return simulations
        .simulate(
            projectionBridge.projectCurrentYearEnd(profile, assumptions),
            context.forwardAssumptions().orElseThrow(),
            SimulationScenario.BASE,
            context.asOfYear())
        .years();
  }

  private List<SimulationYear> future(
      ForwardSimulationInput forward, int current, SimulationScenario scenario) {
    if (forward.forwardAssumptions().isEmpty()) return List.of();
    return simulations
        .simulate(forward.bridgedProfile(), forward.forwardAssumptions().orElseThrow(), scenario,
            forward.context().asOfYear())
        .years();
  }

  private SimulationAssumptions assumptionsForYear(SimulationAssumptions assumptions, int year) {
    int offset = year - assumptions.startYear();
    return new SimulationAssumptions(
        assumptions.currentAge() + offset,
        assumptions.endAge(),
        growForYears(
            assumptions.annualLivingExpenses(), assumptions.effectiveSpendingGrowthRate(), offset),
        assumptions.inflationRate(),
        assumptions.cashReturnRate(),
        assumptions.fixedIncomeReturnRate(),
        assumptions.equityReturnRate(),
        assumptions.realEstateReturnRate(),
        assumptions.otherReturnRate(),
        assumptions.pensionStartAge(),
        assumptions.annualPension(),
        assumptions.capitalGainTaxRate(),
        year,
        growForYears(
            assumptions.annualDiscretionaryExpenses(),
            assumptions.effectiveSpendingGrowthRate(),
            offset),
        assumptions.futureEvents(),
        assumptions.rentalIncomeGrowthSpread(),
        assumptions.spendingGrowthSpread(),
        assumptions.fundingStrategy(),
        assumptions.safeReserveYears(),
        assumptions.equityHarvestMinimumReturnRate(),
        assumptions.equityGainHarvestRate(),
        assumptions.allowEmergencyEquityWithdrawal(),
        assumptions.retirementAge(),
        assumptions.annualEmploymentIncome(),
        assumptions.annualPreRetirementContribution(),
        assumptions.fundingOrder(),
        assumptions.expenseProfile().rebasedAt(offset));
  }

  private Map<PlanningMetric, PlanningMetricValue> deriveHistoricalMarket(
      Long portfolioId, int year) {
    HistoricalPortfolioYear source = historicalPortfolio.read(portfolioId, year);
    if (!source.complete()) return Map.of();
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    result.put(
        PlanningMetric.MARKET_ASSETS,
        derived(
            PlanningMetric.MARKET_ASSETS,
            source.endMarketAssets(),
            PlanningValueSource.ACCOUNTING_DERIVED));
    result.put(
        PlanningMetric.MARKET_INCOME,
        derived(
            PlanningMetric.MARKET_INCOME,
            source.marketIncome(),
            PlanningValueSource.ACCOUNTING_DERIVED));
    result.put(
        PlanningMetric.MARKET_WITHDRAWAL,
        derived(
            PlanningMetric.MARKET_WITHDRAWAL,
            source.netWithdrawal(),
            PlanningValueSource.ACCOUNTING_DERIVED));
    if (source.marketReturn() != null)
      result.put(
          PlanningMetric.MARKET_RETURN,
          derived(
              PlanningMetric.MARKET_RETURN,
              source.marketReturn(),
              PlanningValueSource.ACCOUNTING_DERIVED));
    return result;
  }

  private Map<PlanningMetric, PlanningMetricValue> deriveHistoricalLongTermAssets(
      Long portfolioId, int year) {
    if (historicalLongTermAssets == null) return Map.of();
    HistoricalLongTermAssetYearSource.HistoricalLongTermAssetYear source =
        historicalLongTermAssets.read(portfolioId, year);
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    putHistoricalLongTerm(
        result,
        PlanningMetric.RENTAL_INCOME,
        source.rentalIncomeAvailable(),
        source.rentalIncome());
    putHistoricalLongTerm(
        result,
        PlanningMetric.REAL_ESTATE,
        source.realEstateValueAvailable(),
        source.realEstateValue());
    putHistoricalLongTerm(
        result, PlanningMetric.BOND_VALUE, source.bondValueAvailable(), source.bondValue());
    putHistoricalLongTerm(
        result, PlanningMetric.BOND_INCOME, source.bondIncomeAvailable(), source.bondIncome());
    putHistoricalLongTerm(
        result,
        PlanningMetric.CASH_RESERVE_VALUE,
        source.cashReserveValueAvailable(),
        source.cashReserveValue());
    return result;
  }

  private static void putHistoricalLongTerm(
      Map<PlanningMetric, PlanningMetricValue> result,
      PlanningMetric metric,
      boolean available,
      BigDecimal value) {
    result.put(
        metric,
        available
            ? derived(metric, value, PlanningValueSource.LONG_TERM_DERIVED)
            : unavailable(metric));
  }

  private Map<PlanningMetric, PlanningMetricValue> currentActual(InvestmentProfile profile) {
    Map<EconomicBucket, BigDecimal> allocation = new EnumMap<>(EconomicBucket.class);
    profile
        .allocations()
        .forEach(value -> allocation.merge(value.bucket(), value.value(), BigDecimal::add));
    BigDecimal manualReserve =
        profile.longTermAssets().stream()
            .filter(asset -> asset.type() == LongTermAssetTypeModel.CASH_RESERVE)
            .map(ProjectedLongTermAsset::currentValue)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal locked =
        profile.longTermAssets().stream()
            .filter(
                asset ->
                    asset.type() == LongTermAssetTypeModel.BOND
                        || asset.type() == LongTermAssetTypeModel.DEPOSIT)
            .map(ProjectedLongTermAsset::currentValue)
            .reduce(ZERO, BigDecimal::add);
    BigDecimal fixed =
        allocation
            .getOrDefault(EconomicBucket.FIXED_INCOME, ZERO)
            .subtract(
                profile.longTermAssets().stream()
                    .filter(asset -> asset.type() == LongTermAssetTypeModel.BOND)
                    .map(ProjectedLongTermAsset::currentValue)
                    .reduce(ZERO, BigDecimal::add))
            .max(ZERO);
    BigDecimal safeReserve =
        allocation
            .getOrDefault(EconomicBucket.LIQUID_CASH, ZERO)
            .add(allocation.getOrDefault(EconomicBucket.FIXED_INCOME, ZERO))
            .subtract(locked)
            .max(ZERO);
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    result.put(
        PlanningMetric.NET_WORTH,
        derived(
            PlanningMetric.NET_WORTH,
            profile.totalNetWorth(),
            PlanningValueSource.PORTFOLIO_DERIVED));
    result.put(
        PlanningMetric.MARKET_ASSETS,
        derived(
            PlanningMetric.MARKET_ASSETS,
            profile.marketPortfolioValue(),
            PlanningValueSource.PORTFOLIO_DERIVED));
    result.put(
        PlanningMetric.SAFE_RESERVE,
        derived(PlanningMetric.SAFE_RESERVE, safeReserve, PlanningValueSource.PORTFOLIO_DERIVED));
    result.put(
        PlanningMetric.CASH_RESERVE_VALUE,
        derived(
            PlanningMetric.CASH_RESERVE_VALUE,
            profile.retirementReserve(),
            PlanningValueSource.PORTFOLIO_DERIVED));
    result.put(
        PlanningMetric.MANUAL_LIQUID_RESERVE,
        derived(
            PlanningMetric.MANUAL_LIQUID_RESERVE,
            manualReserve,
            PlanningValueSource.LONG_TERM_DERIVED));
    result.put(
        PlanningMetric.FIXED_INCOME,
        derived(PlanningMetric.FIXED_INCOME, fixed, PlanningValueSource.PORTFOLIO_DERIVED));
    result.put(
        PlanningMetric.EQUITY,
        derived(
            PlanningMetric.EQUITY,
            allocation.getOrDefault(EconomicBucket.EQUITY, ZERO),
            PlanningValueSource.PORTFOLIO_DERIVED));
    result.put(
        PlanningMetric.REAL_ESTATE,
        derived(
            PlanningMetric.REAL_ESTATE,
            allocation.getOrDefault(EconomicBucket.REAL_ESTATE, ZERO),
            PlanningValueSource.LONG_TERM_DERIVED));
    return result;
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

  private PastPlanningYear past(PlanningYearEntity year) {
    return new PastPlanningYear(
        year.getYear(),
        year.getStatus(),
        year.getClosedAt(),
        year.getBaselinePlanId(),
        year.getBaselineRevisionId(),
        actualValues(year),
        baselineValues(year));
  }

  private Map<PlanningMetric, PlanningMetricValue> actualValues(PlanningYearEntity year) {
    return records(year, PlanningValueKind.ACTUAL);
  }

  private Map<PlanningMetric, PlanningMetricValue> baselineValues(PlanningYearEntity year) {
    return records(year, PlanningValueKind.BASELINE);
  }

  private Map<PlanningMetric, PlanningMetricValue> records(
      PlanningYearEntity year, PlanningValueKind kind) {
    Map<PlanningMetric, PlanningMetricValue> result = new EnumMap<>(PlanningMetric.class);
    values
        .findAllByPlanningYearIdAndValueKind(year.getId(), kind)
        .forEach(
            value ->
                result.put(
                    value.getMetric(),
                    new PlanningMetricValue(
                        value.getMetric(),
                        value.getDerivedValue(),
                        value.getApprovedValue(),
                        value.getSourceType(),
                        value.getNote())));
    return result;
  }

  private PlanningYearEntity year(Long portfolioId, int calendarYear) {
    return years
        .findByPortfolioIdAndYear(portfolioId, calendarYear)
        .orElseGet(
            () -> {
              PlanningYearEntity created = new PlanningYearEntity();
              created.setPortfolioId(portfolioId);
              created.setYear(calendarYear);
              created.setStatus(PlanningYearStatus.DRAFT);
              return years.save(created);
            });
  }

  private PlanningYearEntity get(Long portfolioId, int calendarYear) {
    return years
        .findByPortfolioIdAndYear(portfolioId, calendarYear)
        .orElseThrow(() -> new NoSuchElementException("Planning year not found"));
  }

  private void upsert(PlanningYearEntity year, PlanningValueKind kind, PlanningMetricValue value) {
    PlanningYearValueEntity stored =
        values
            .findByPlanningYearIdAndValueKindAndMetric(year.getId(), kind, value.metric())
            .orElseGet(PlanningYearValueEntity::new);
    stored.setPlanningYearId(year.getId());
    stored.setValueKind(kind);
    stored.setMetric(value.metric());
    stored.setDerivedValue(value.derivedValue());
    stored.setApprovedValue(value.approvedValue());
    stored.setSourceType(value.source());
    stored.setNote(value.note());
    values.save(stored);
  }

  private void save(PlanningYearEntity year, PlanningValueKind kind, PlanningMetricValue value) {
    upsert(year, kind, value);
  }

  private static PlanningMetricValue derived(
      PlanningMetric metric, BigDecimal amount, PlanningValueSource source) {
    return new PlanningMetricValue(metric, amount, null, source, null);
  }

  private static PlanningMetricValue unavailable(PlanningMetric metric) {
    return new PlanningMetricValue(
        metric, null, null, PlanningValueSource.UNAVAILABLE, unavailableNote(metric));
  }

  private CurrentPlanningYear currentForTimeline(
      Long portfolioId,
      int year,
      InvestmentProfile profile,
      SimulationAssumptions assumptions,
      CurrentYearBridgeResult bridge) {
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
    if (longTermAssets != null) {
      LongTermAssetAnnualSnapshotModel facts =
          longTermAssets.currentAnnualSnapshot(portfolioId, LocalDate.now(clock));
      putCurrentFact(live, PlanningMetric.RENTAL_INCOME, facts.rentalIncome());
      putCurrentFact(live, PlanningMetric.BOND_VALUE, facts.bondValue());
      putCurrentFact(live, PlanningMetric.BOND_INCOME, facts.bondIncome());
    }
    BigDecimal costs =
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses());
    BigDecimal rental = planningAmount(live, PlanningMetric.RENTAL_INCOME);
    BigDecimal bond = planningAmount(live, PlanningMetric.BOND_INCOME);
    int currentAge = ForwardSimulationContextFactory.currentPlanningAge(assumptions, year);
    BigDecimal employment =
        currentAge < assumptions.retirementAge() ? assumptions.annualEmploymentIncome() : ZERO;
    BigDecimal pension =
        currentAge >= assumptions.pensionStartAge() ? assumptions.annualPension() : ZERO;
    if (rental != null || bond != null) {
      BigDecimal eventIncome =
          assumptions.futureEvents().stream()
              .filter(event -> event.year() == year && event.type() == SimulationEventType.ONE_OFF_INCOME)
              .map(SimulationEvent::amount)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal eventExpenses =
          assumptions.futureEvents().stream()
              .filter(event -> event.year() == year && event.type() == SimulationEventType.ONE_OFF_EXPENSE)
              .map(SimulationEvent::amount)
              .reduce(ZERO, BigDecimal::add);
      BigDecimal funding =
          costs
              .add(eventExpenses)
              .subtract(zero(rental))
              .subtract(zero(bond))
              .subtract(employment)
              .subtract(pension)
              .subtract(eventIncome)
              .max(ZERO);
      live.put(
          PlanningMetric.PORTFOLIO_FUNDING,
          derived(
              PlanningMetric.PORTFOLIO_FUNDING, funding, PlanningValueSource.SIMULATION_BASELINE));
    }
    Map<PlanningMetric, PlanningMetricValue> expected = new EnumMap<>(PlanningMetric.class);
    expected.putAll(current.expectedValues());
    if (bridge != null) {
      putExpectedBucket(expected, PlanningMetric.CASH_RESERVE_VALUE, bridge.expectedEnd(BucketType.CASH));
      putExpectedBucket(expected, PlanningMetric.SAFE_RESERVE, bridge.expectedEnd(BucketType.CASH));
      putExpectedBucket(expected, PlanningMetric.FIXED_INCOME, bridge.expectedEnd(BucketType.BONDS));
      putExpectedBucket(expected, PlanningMetric.BOND_VALUE, bridge.expectedEnd(BucketType.BONDS));
      putExpectedBucket(expected, PlanningMetric.EQUITY, bridge.expectedEnd(BucketType.EQUITIES));
      putExpectedBucket(expected, PlanningMetric.REAL_ESTATE, bridge.expectedEnd(BucketType.REAL_ESTATE));
      putExpectedBucket(expected, PlanningMetric.NET_WORTH, bridge.bridgedProfile().totalNetWorth());
    }
    return new CurrentPlanningYear(
        current.year(),
        current.baselinePlanId(),
        current.baselineRevisionId(),
        current.baselineCreatedAt(),
        live,
        expected);
  }

  private static void putExpectedBucket(
      Map<PlanningMetric, PlanningMetricValue> values,
      PlanningMetric metric,
      BigDecimal amount) {
    if (amount != null) values.put(metric, derived(metric, amount, PlanningValueSource.SIMULATION_BASELINE));
  }

  private static void putCurrentFact(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric, BigDecimal amount) {
    if (amount != null)
      values.put(metric, derived(metric, amount, PlanningValueSource.LONG_TERM_DERIVED));
  }

  private static BigDecimal planningAmount(
      Map<PlanningMetric, PlanningMetricValue> values, PlanningMetric metric) {
    PlanningMetricValue value = values.get(metric);
    return value == null ? null : value.value();
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? ZERO : value;
  }

  private static String unavailableNote(PlanningMetric metric) {
    return switch (metric) {
      case CORE_SPENDING, DISCRETIONARY_SPENDING -> "Required historical planning input";
      case NET_WORTH -> "Optional; Market assets provide closure anchor";
      case REAL_ESTATE, BOND_VALUE, BOND_INCOME, CASH_RESERVE_VALUE, RENTAL_INCOME ->
          "Historical value unavailable";
      default -> "Historical value unavailable";
    };
  }

  private static BigDecimal grow(BigDecimal amount, BigDecimal rate) {
    return amount.multiply(BigDecimal.ONE.add(rate));
  }

  private static BigDecimal growForYears(BigDecimal amount, BigDecimal rate, int years) {
    BigDecimal result = amount;
    for (int year = 0; year < Math.max(0, years); year++) result = grow(result, rate);
    return result;
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

  private void validateManualEdit(PlanningMetric metric, PlanningYearValueEntity current) {
    if (!isManualEditAllowed(metric, current)) {
      if (metric == PlanningMetric.REAL_ESTATE
          && current != null
          && current.getDerivedValue() != null
          && current.getSourceType() != PlanningValueSource.UNAVAILABLE) {
        throw new IllegalArgumentException("Derived real-estate value cannot be overridden");
      }
      throw new IllegalArgumentException(
          metric + " is derived from authoritative portfolio data and cannot be overridden");
    }
  }

  private static boolean isManualEditAllowed(
      PlanningMetric metric, PlanningYearValueEntity current) {
    if (!metric.isManualEditable()) return false;
    return metric != PlanningMetric.REAL_ESTATE
        || current == null
        || current.getDerivedValue() == null
        || current.getSourceType() == PlanningValueSource.UNAVAILABLE;
  }

  private void ensureComplete(PlanningYearEntity planningYear) {
    PlanningYearCloseStatus status = closeStatus(planningYear);
    if (!status.canClose())
      throw new IllegalStateException(
          "Cannot close "
              + planningYear.getYear()
              + ". Missing: "
              + String.join(", ", status.missingMetrics()));
  }

  private static PlanningTimelineState state(PlanningYearEntity year) {
    return year.getStatus() == PlanningYearStatus.CLOSED
        ? PlanningTimelineState.ACTUAL
        : PlanningTimelineState.NEEDS_REVIEW;
  }

  private void ensureClosedTimestamp(PlanningYearEntity planningYear) {
    if (planningYear.getClosedAt() == null) {
      planningYear.setClosedAt(Instant.now(clock));
      years.save(planningYear);
    }
  }

  private PlanningYearCloseStatus closeStatus(PlanningYearEntity planningYear) {
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

  private static int age(SimulationAssumptions assumptions, int year) {
    return assumptions.currentAge() + year - assumptions.startYear();
  }
}
