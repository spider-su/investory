package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformance;
import com.smartbox.investory.infrastructure.repository.portfolio.PortfolioMonthlyPerformanceRepository;
import com.smartbox.investory.investment.infrastructure.read.HistoricalPortfolioActualsReadService;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshot;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.retirement.infrastructure.planning.*;
import com.smartbox.investory.retirement.profile.*;
import com.smartbox.investory.retirement.simulation.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningTimelineFacadeTest {
  @Mock PlanningYearRepository years;
  @Mock PlanningYearValueRepository values;
  @Mock PortfolioMonthlyPerformanceRepository performance;
  @Mock RetirementSimulationService simulations;
  @Mock CurrentYearProjectionBridge projectionBridge;
  @Mock HistoricalLongTermAssetYearSource longTermAssets;
  @Mock LongTermAssetAnnualSnapshotReader currentLongTermAssets;
  PlanningTimelineFacade facade;
  PlanningYear planningYear;

  @BeforeEach
  void setUp() {
    facade =
        new PlanningTimelineFacade(
            years,
            values,
            new HistoricalPortfolioActualsReadService(performance),
            longTermAssets,
            simulations,
            projectionBridge,
            Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC),
            new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)),
            currentLongTermAssets);
    planningYear = new PlanningYear();
    planningYear.setId(7L);
    planningYear.setPortfolioId(1L);
    planningYear.setYear(2026);
    planningYear.setStatus(PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2026)).thenReturn(Optional.of(planningYear));
    when(values.findAllByPlanningYearIdAndValueKind(anyLong(), any())).thenReturn(List.of());
    when(values.findByPlanningYearIdAndValueKindAndMetric(anyLong(), any(), any()))
        .thenReturn(Optional.empty());
    when(values.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(years.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(projectionBridge.projectCurrentYearEnd(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    lenient()
        .when(currentLongTermAssets.currentAnnualSnapshot(eq(1L), any(LocalDate.class)))
        .thenReturn(
            new LongTermAssetAnnualSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));
    lenient()
        .when(longTermAssets.read(anyLong(), anyInt()))
        .thenReturn(HistoricalLongTermAssetYearSource.HistoricalLongTermAssetYear.unavailable());
  }

  @Test
  void closeCopiesLivePlanningValuesAndNeverWritesPortfolioFacts() {
    assertThrows(
        IllegalArgumentException.class, () -> facade.closeCurrentYear(1L, 2026, profile()));
    verifyNoInteractions(performance, simulations);
  }

  @Test
  void closedYearRejectsCasualManualEditsAndReopenIsExplicit() {
    PlanningYear historical = new PlanningYear();
    historical.setId(8L);
    historical.setPortfolioId(1L);
    historical.setYear(2025);
    historical.setStatus(PlanningYearStatus.CLOSED);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(historical));
    assertThrows(
        IllegalStateException.class,
        () ->
            facade.saveDraftManualValue(
                1L, 2025, PlanningMetric.CORE_SPENDING, BigDecimal.TEN, "manual"));
    assertFalse(facade.isHistoricalMetricEditable(1L, 2025, PlanningMetric.CORE_SPENDING));
    facade.reopenHistoricalYear(1L, 2025);
    assertEquals(PlanningYearStatus.DRAFT, historical.getStatus());
    assertNotNull(historical.getReopenedAt());
  }

  @Test
  void refreshesStaleAccountingWithdrawalButPreservesUserOverride() {
    PlanningYear historical = new PlanningYear();
    historical.setId(8L);
    historical.setPortfolioId(1L);
    historical.setYear(2025);
    historical.setStatus(PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(historical));

    PlanningYearValue staleWithdrawal =
        stored(
            PlanningMetric.MARKET_WITHDRAWAL, "740429.67", PlanningValueSource.ACCOUNTING_DERIVED);
    PlanningYearValue userCosts =
        stored(PlanningMetric.CORE_SPENDING, "180000", PlanningValueSource.USER_OVERRIDE);
    when(values.findByPlanningYearIdAndValueKindAndMetric(
            8L, PlanningValueKind.ACTUAL, PlanningMetric.MARKET_WITHDRAWAL))
        .thenReturn(Optional.of(staleWithdrawal));
    when(values.findByPlanningYearIdAndValueKindAndMetric(
            8L, PlanningValueKind.ACTUAL, PlanningMetric.CORE_SPENDING))
        .thenReturn(Optional.of(userCosts));
    when(performance.findByPortfolioIdAndMonthBetweenOrderByMonthAsc(anyLong(), any(), any()))
        .thenAnswer(
            invocation -> {
              int year = ((LocalDate) invocation.getArgument(1)).getYear();
              if (year != 2025) return List.of();
              List<PortfolioMonthlyPerformance> rows = new ArrayList<>();
              for (int month = 1; month <= 12; month++) {
                PortfolioMonthlyPerformance row = mock(PortfolioMonthlyPerformance.class);
                when(row.getMonth()).thenReturn(LocalDate.of(2025, month, 1));
                when(row.getEndEquityDecimal()).thenReturn(new BigDecimal("380333.75"));
                when(row.getDepositFlowDecimal()).thenReturn(new BigDecimal("58333.3333333333"));
                when(row.getWithdrawalFlowDecimal()).thenReturn(new BigDecimal("61702.4725"));
                when(row.getDividendsDecimal()).thenReturn(BigDecimal.ZERO);
                when(row.getInterestDecimal()).thenReturn(BigDecimal.ZERO);
                when(row.getReturnPctDecimal()).thenReturn(BigDecimal.ZERO);
                rows.add(row);
              }
              return rows;
            });

    facade.refreshHistoricalAccounting(1L, 2025);

    ArgumentCaptor<PlanningYearValue> saved = ArgumentCaptor.forClass(PlanningYearValue.class);
    verify(values, atLeastOnce()).save(saved.capture());
    PlanningYearValue refreshedWithdrawal =
        saved.getAllValues().stream()
            .filter(value -> value.getMetric() == PlanningMetric.MARKET_WITHDRAWAL)
            .reduce((first, second) -> second)
            .orElseThrow();
    assertEquals(
        new BigDecimal("40429.67"),
        refreshedWithdrawal.getDerivedValue().setScale(2, RoundingMode.HALF_UP));
    assertEquals(PlanningValueSource.ACCOUNTING_DERIVED, refreshedWithdrawal.getSourceType());
    verify(values, never())
        .save(
            argThat(
                value ->
                    value.getMetric() == PlanningMetric.CORE_SPENDING
                        && new BigDecimal("180000").equals(value.getApprovedValue())));
  }

  @Test
  void closedYearCannotRefreshAccountingValues() {
    PlanningYear closed = new PlanningYear();
    closed.setId(9L);
    closed.setPortfolioId(1L);
    closed.setYear(2025);
    closed.setStatus(PlanningYearStatus.CLOSED);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(closed));
    assertThrows(IllegalStateException.class, () -> facade.refreshHistoricalAccounting(1L, 2025));
    verifyNoInteractions(performance);
  }

  private static PlanningYearValue stored(
      PlanningMetric metric, String amount, PlanningValueSource source) {
    PlanningYearValue value = new PlanningYearValue();
    value.setPlanningYearId(8L);
    value.setValueKind(PlanningValueKind.ACTUAL);
    value.setMetric(metric);
    value.setDerivedValue(new BigDecimal(amount));
    value.setSourceType(source);
    return value;
  }

  @Test
  void historicalRealEstateIsNeverAPlanningInput() {
    PlanningYearValue derived = new PlanningYearValue();
    derived.setDerivedValue(BigDecimal.TEN);
    derived.setSourceType(PlanningValueSource.LONG_TERM_DERIVED);
    when(values.findByPlanningYearIdAndValueKindAndMetric(
            7L, PlanningValueKind.ACTUAL, PlanningMetric.REAL_ESTATE))
        .thenReturn(Optional.of(derived));
    assertFalse(facade.isHistoricalMetricEditable(1L, 2026, PlanningMetric.REAL_ESTATE));
    PlanningYearValue unavailable = new PlanningYearValue();
    unavailable.setSourceType(PlanningValueSource.UNAVAILABLE);
    when(values.findByPlanningYearIdAndValueKindAndMetric(
            7L, PlanningValueKind.ACTUAL, PlanningMetric.REAL_ESTATE))
        .thenReturn(Optional.of(unavailable));
    assertFalse(facade.isHistoricalMetricEditable(1L, 2026, PlanningMetric.REAL_ESTATE));
  }

  @Test
  void authoritativeMetricsCannotBeOverriddenButPlanningSpendingCan() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            facade.saveCurrentManualValue(
                1L, 2026, PlanningMetric.NET_WORTH, BigDecimal.TEN, "no"));
    facade.saveCurrentManualValue(1L, 2026, PlanningMetric.CORE_SPENDING, BigDecimal.TEN, "manual");
    verify(values)
        .save(
            argThat(
                value ->
                    value.getMetric() == PlanningMetric.CORE_SPENDING
                        && BigDecimal.TEN.equals(value.getApprovedValue())));
  }

  @Test
  void incompleteHistoricalDraftCannotClose() {
    PlanningYear past = new PlanningYear();
    past.setId(8L);
    past.setPortfolioId(1L);
    past.setYear(2025);
    past.setStatus(PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(past));
    when(values.findAllByPlanningYearIdAndValueKind(8L, PlanningValueKind.ACTUAL))
        .thenReturn(List.of());
    assertThrows(IllegalStateException.class, () -> facade.closeHistoricalDraft(1L, 2025));
  }

  @Test
  void historicalCloseStatusUsesTheSameCompletenessPolicyAsClose() {
    PlanningYear past = new PlanningYear();
    past.setId(8L);
    past.setPortfolioId(1L);
    past.setYear(2025);
    past.setStatus(PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(past));
    when(values.findAllByPlanningYearIdAndValueKind(8L, PlanningValueKind.ACTUAL))
        .thenReturn(List.of());
    PlanningYearCloseStatus status = facade.historicalCloseStatus(1L, 2025);
    assertFalse(status.canClose());
    assertTrue(
        status
            .missingMetrics()
            .containsAll(
                List.of("Net worth or market assets", "Annual living costs", "Annual extras")));
  }

  @Test
  void baselineStoresOnlySimulationExpectationAndIsIndependentOfLiveFacts() {
    SimulationYear projected = projected();
    when(simulations.simulate(eq(profile()), any(), eq(SimulationScenario.BASE)))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(projected)));
    facade.setCurrentBaseline(1L, 2026, 4L, 55L, profile(), assumptions());
    assertEquals(4L, planningYear.getBaselinePlanId());
    assertEquals(55L, planningYear.getBaselineRevisionId());
    verify(values, atLeastOnce())
        .save(
            argThat(
                value ->
                    value.getValueKind() == PlanningValueKind.BASELINE
                        && value.getSourceType() == PlanningValueSource.SIMULATION_BASELINE));
    verifyNoInteractions(performance);
  }

  @Test
  void timelineOrdersPastThenLiveThenNextYearProjectionWithoutDuplicatingCurrentYear() {
    PlanningYear past = new PlanningYear();
    past.setId(8L);
    past.setPortfolioId(1L);
    past.setYear(2025);
    past.setStatus(PlanningYearStatus.CLOSED);
    when(years.findAllByPortfolioIdOrderByYearAsc(1L)).thenReturn(List.of(past));
    when(values.findAllByPlanningYearIdAndValueKind(eq(8L), any())).thenReturn(List.of());
    when(simulations.simulate(eq(profile()), any(), eq(SimulationScenario.BASE)))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(projected())));
    PlanningTimeline timeline = facade.loadTimeline(1L, profile(), assumptions());
    assertEquals(
        List.of(2025, 2026, 2027),
        timeline.years().stream().map(PlanningTimelineYear::year).toList());
    assertEquals(
        List.of(
            PlanningTimelineState.ACTUAL,
            PlanningTimelineState.LIVE,
            PlanningTimelineState.PROJECTED),
        timeline.years().stream().map(PlanningTimelineYear::state).toList());
  }

  @Test
  void liveTimelineUsesStartingAssetsCanonicalIncomeAndActivePlanFunding() {
    when(years.findAllByPortfolioIdOrderByYearAsc(1L)).thenReturn(List.of());
    when(currentLongTermAssets.currentAnnualSnapshot(eq(1L), any(LocalDate.class)))
        .thenReturn(
            new LongTermAssetAnnualSnapshot(
                new BigDecimal("3650000"),
                new BigDecimal("180000"),
                new BigDecimal("900000"),
                new BigDecimal("48000"),
                new BigDecimal("100000"),
                BigDecimal.ZERO));
    SimulationAssumptions assumptions = workingAssumptions();
    ForwardSimulationContext context =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC))
            .create(profile(), assumptions);
    PlanningTimeline timeline =
        facade.loadForwardTimeline(
            1L, profile(), new ForwardSimulationInput(context, profile(), Optional.empty()));

    CurrentPlanningYear live = timeline.years().getFirst().current();
    assertEquals(
        new BigDecimal("192000"), live.actualValues().get(PlanningMetric.CORE_SPENDING).value());
    assertEquals(
        new BigDecimal("70000"),
        live.actualValues().get(PlanningMetric.DISCRETIONARY_SPENDING).value());
    assertEquals(
        new BigDecimal("180000"), live.actualValues().get(PlanningMetric.RENTAL_INCOME).value());
    assertEquals(
        new BigDecimal("900000"), live.actualValues().get(PlanningMetric.BOND_VALUE).value());
    assertEquals(
        new BigDecimal("48000"), live.actualValues().get(PlanningMetric.BOND_INCOME).value());
    assertEquals(
        BigDecimal.ZERO, live.actualValues().get(PlanningMetric.PORTFOLIO_FUNDING).value());
    verify(currentLongTermAssets).currentAnnualSnapshot(1L, LocalDate.of(2026, 8, 14));
    verifyNoInteractions(simulations, projectionBridge);
  }

  @Test
  void historicalDraftDerivesRentalIncomeFromLongTermAssetSource() {
    PlanningYear historical = new PlanningYear();
    historical.setId(8L);
    historical.setPortfolioId(1L);
    historical.setYear(2025);
    historical.setStatus(PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(historical));
    when(longTermAssets.read(1L, 2025))
        .thenReturn(
            new HistoricalLongTermAssetYearSource.HistoricalLongTermAssetYear(
                true, new BigDecimal("1234")));
    PlanningTimelineFacade withLongTermSource =
        new PlanningTimelineFacade(
            years,
            values,
            new HistoricalPortfolioActualsReadService(performance),
            longTermAssets,
            simulations,
            projectionBridge,
            Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC),
            new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)));

    withLongTermSource.createHistoricalDraft(1L, 2025);

    verify(values)
        .save(
            argThat(
                value ->
                    value.getMetric() == PlanningMetric.RENTAL_INCOME
                        && new BigDecimal("1234").equals(value.getDerivedValue())
                        && value.getSourceType() == PlanningValueSource.LONG_TERM_DERIVED));
  }

  @Test
  void currentTimelineDoesNotExposeOrphanBaselineRowsWithoutRevisionProvenance() {
    PlanningYearValue stale = new PlanningYearValue();
    stale.setPlanningYearId(7L);
    stale.setValueKind(PlanningValueKind.BASELINE);
    stale.setMetric(PlanningMetric.CORE_SPENDING);
    stale.setDerivedValue(new BigDecimal("668002"));
    when(values.findAllByPlanningYearIdAndValueKind(7L, PlanningValueKind.BASELINE))
        .thenReturn(List.of(stale));

    CurrentPlanningYear current = facade.current(1L, 2026, profile());

    assertNull(current.baselinePlanId());
    assertTrue(current.expectedValues().isEmpty());
  }

  @Test
  void completeMarketAssetsAllowCloseWhenHistoricalNetWorthIsUnavailable() {
    PlanningYear historical = new PlanningYear();
    historical.setId(8L);
    historical.setPortfolioId(1L);
    historical.setYear(2025);
    historical.setStatus(PlanningYearStatus.DRAFT);
    when(years.findByPortfolioIdAndYear(1L, 2025)).thenReturn(Optional.of(historical));
    when(values.findAllByPlanningYearIdAndValueKind(8L, PlanningValueKind.ACTUAL))
        .thenReturn(
            List.of(
                stored(PlanningMetric.MARKET_ASSETS, "100"),
                stored(PlanningMetric.CORE_SPENDING, "50"),
                stored(PlanningMetric.DISCRETIONARY_SPENDING, "25")));

    PastPlanningYear closed = facade.closeHistoricalDraft(1L, 2025);

    assertEquals(PlanningYearStatus.CLOSED, historical.getStatus());
    assertNotNull(historical.getClosedAt());
    assertNull(closed.values().get(PlanningMetric.NET_WORTH));
    assertEquals(new BigDecimal("100"), closed.values().get(PlanningMetric.MARKET_ASSETS).value());
  }

  @Test
  void historicalDraftIsShownAsNeedsReviewRatherThanFinalActual() {
    PlanningYear past = new PlanningYear();
    past.setId(8L);
    past.setPortfolioId(1L);
    past.setYear(2025);
    past.setStatus(PlanningYearStatus.DRAFT);
    when(years.findAllByPortfolioIdOrderByYearAsc(1L)).thenReturn(List.of(past));
    ForwardSimulationContext context =
        new ForwardSimulationContextFactory(
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC))
            .create(profile(), assumptions());
    ForwardSimulationInput forward =
        new ForwardSimulationInput(context, profile(), Optional.empty());

    PlanningTimeline timeline = facade.loadForwardTimeline(1L, profile(), forward);

    assertEquals(PlanningTimelineState.NEEDS_REVIEW, timeline.years().getFirst().state());
  }

  @Test
  void futureProjectionReceivesTheBridgedCurrentYearEndProfile() {
    InvestmentProfile bridged =
        new InvestmentProfile(
            1L,
            CurrencyType.USD,
            new BigDecimal("1200"),
            BigDecimal.ZERO,
            new BigDecimal("1200"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("1200"),
            BigDecimal.ZERO,
            List.of(),
            List.of());
    when(years.findAllByPortfolioIdOrderByYearAsc(1L)).thenReturn(List.of());
    when(projectionBridge.projectCurrentYearEnd(profile(), assumptions())).thenReturn(bridged);
    when(simulations.simulate(eq(bridged), any(), eq(SimulationScenario.BASE)))
        .thenReturn(
            new SimulationResult(
                SimulationScenario.BASE,
                false,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(projected())));
    PlanningTimeline timeline = facade.loadTimeline(1L, profile(), assumptions());
    assertEquals(2027, timeline.years().getLast().year());
    verify(simulations).simulate(eq(bridged), any(), eq(SimulationScenario.BASE));
  }

  private static InvestmentProfile profile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        new BigDecimal("700"),
        new BigDecimal("300"),
        new BigDecimal("1000"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("700"),
        new BigDecimal("300"),
        List.of(
            new ProfileAllocation(
                EconomicBucket.LIQUID_CASH,
                new BigDecimal("100"),
                BigDecimal.ONE,
                Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.FIXED_INCOME,
                new BigDecimal("200"),
                BigDecimal.ONE,
                Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.EQUITY, new BigDecimal("400"), BigDecimal.ONE, Liquidity.LIQUID),
            new ProfileAllocation(
                EconomicBucket.REAL_ESTATE,
                new BigDecimal("300"),
                BigDecimal.ONE,
                Liquidity.ILLIQUID)),
        List.of());
  }

  private static SimulationAssumptions assumptions() {
    return new SimulationAssumptions(
        40,
        42,
        new BigDecimal("100"),
        new BigDecimal("0.025"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("0.06"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        67,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        2026,
        BigDecimal.ZERO,
        List.of(),
        new BigDecimal("0.02"),
        new BigDecimal("0.025"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("0.07"),
        new BigDecimal("0.75"),
        true);
  }

  private static SimulationAssumptions workingAssumptions() {
    return new SimulationAssumptions(
        41,
        80,
        new BigDecimal("192000"),
        new BigDecimal("0.014"),
        new BigDecimal("0.04"),
        new BigDecimal("0.05"),
        new BigDecimal("0.085"),
        BigDecimal.ZERO,
        new BigDecimal("0.04"),
        67,
        new BigDecimal("7000"),
        BigDecimal.ZERO,
        2026,
        new BigDecimal("70000"),
        List.of(),
        new BigDecimal("0.01"),
        new BigDecimal("0.014"),
        SimulationFundingStrategy.RESERVE_AND_HARVEST,
        new BigDecimal("5"),
        new BigDecimal("0.08"),
        new BigDecimal("0.95"),
        true,
        42,
        new BigDecimal("240000"),
        new BigDecimal("60000"));
  }

  private static PlanningYearValue stored(PlanningMetric metric, String amount) {
    PlanningYearValue value = new PlanningYearValue();
    value.setMetric(metric);
    value.setDerivedValue(new BigDecimal(amount));
    value.setSourceType(PlanningValueSource.ACCOUNTING_DERIVED);
    return value;
  }

  private static SimulationYear projected() {
    BigDecimal z = BigDecimal.ZERO;
    return new SimulationYear(
        41,
        0,
        new BigDecimal("1000"),
        new BigDecimal("100"),
        z,
        z,
        new BigDecimal("100"),
        z,
        z,
        z,
        z,
        new BigDecimal("100"),
        new BigDecimal("100"),
        z,
        new BigDecimal("100"),
        new BigDecimal("300"),
        new BigDecimal("500"),
        new BigDecimal("300"),
        z,
        new BigDecimal("0.06"),
        z,
        z,
        z,
        new BigDecimal("100"),
        new BigDecimal("100"),
        new BigDecimal("200"),
        new BigDecimal("200"),
        new BigDecimal("400"),
        new BigDecimal("400"),
        new BigDecimal("300"),
        new BigDecimal("300"),
        z,
        z,
        z,
        z,
        z,
        z,
        new BigDecimal("700"),
        new BigDecimal("700"),
        new BigDecimal("700"),
        new BigDecimal("300"),
        new BigDecimal("1000"),
        false,
        z);
  }
}
