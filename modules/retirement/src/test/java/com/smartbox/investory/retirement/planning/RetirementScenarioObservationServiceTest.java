package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.PortfolioYtdTwrReader;
import com.smartbox.investory.investment.api.reporting.model.ReturnMetric;
import com.smartbox.investory.longterm.api.BondReturnObservationReader;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.planning.application.*;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetirementScenarioObservationServiceTest {

  @Test
  void liveObservationsUseCurrentAnnualizedFactsAndCanonicalYtdPerformance() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader current = mock(LongTermAssetProfileReader.class);
    BondReturnObservationReader bonds = mock(BondReturnObservationReader.class);
    PortfolioYtdTwrReader performance = mock(PortfolioYtdTwrReader.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    var liveSnapshot =
        snapshot(
            new LongTermAssetAnnualSnapshotModel(
                null, new BigDecimal("169.2"), null, null, null, null));
    when(current.snapshot(7L, java.time.LocalDate.of(2026, 8, 29))).thenReturn(liveSnapshot);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(
                null, new BigDecimal("165.0"), null, null, null, null));
    when(bonds.currentWeightedEffectiveReturn(7L, java.time.LocalDate.of(2026, 8, 29)))
        .thenReturn(new BigDecimal("0.0475"));
    when(performance.ytdTwr(7L)).thenReturn(ReturnMetric.available(new BigDecimal("0.173")));

    var result =
        new RetirementScenarioObservationService(longTerm, current, bonds, performance, clock)
            .load(
                7L,
                new PlanningTimeline(
                    List.of(
                        closedYear(2025, "165"),
                        new PlanningTimelineYear(
                            2026,
                            43,
                            PlanningTimelineState.LIVE,
                            null,
                            new CurrentPlanningYear(
                                2026,
                                null,
                                null,
                                Map.of(
                                    PlanningMetric.CORE_SPENDING, value("169.2"),
                                    PlanningMetric.DISCRETIONARY_SPENDING, value("0")),
                                Map.of(),
                                new BigDecimal("169.2")),
                            null))));

    assertEquals(
        0, new BigDecimal("0.0254545454545454545").compareTo(result.get("Rental growth").value()));
    assertEquals(new BigDecimal("0.0475"), result.get("Bond return").value());
    assertEquals(new BigDecimal("0.173"), result.get("Equity return").value());
    assertEquals("Actual YTD TWR", result.get("Equity return").label());
    assertEquals("2026 YTD", result.get("Equity return").period());
    verify(performance).ytdTwr(7L);
    assertEquals(
        0,
        new BigDecimal("0.0254545454545454545").compareTo(result.get("Spending growth").value()));
  }

  @Test
  void spendingGrowthUsesAnnualizedChangeAcrossLastTwoClosedYears() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader current = mock(LongTermAssetProfileReader.class);
    BondReturnObservationReader bonds = mock(BondReturnObservationReader.class);
    PortfolioYtdTwrReader performance = mock(PortfolioYtdTwrReader.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));
    when(longTerm.historicalAnnualSnapshot(7L, 2024))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));

    var result =
        new RetirementScenarioObservationService(longTerm, current, bonds, performance, clock)
            .load(
                7L,
                new PlanningTimeline(
                    List.of(
                        closedYear(2023, "100"),
                        closedYear(2024, "121"),
                        closedYear(2025, "144"))));

    assertEquals(
        ScenarioObservationAvailability.AVAILABLE, result.get("Spending growth").availability());
    assertEquals(new BigDecimal("0.1900826446280991736"), result.get("Spending growth").value());
    assertEquals(
        ScenarioObservationAvailability.UNAVAILABLE, result.get("Equity return").availability());
    verify(performance, never()).ytdTwr(7L);
  }

  @Test
  void partialYearActualWithoutAnnualizedOutlookIsInsufficientHistory() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader current = mock(LongTermAssetProfileReader.class);
    BondReturnObservationReader bonds = mock(BondReturnObservationReader.class);
    PortfolioYtdTwrReader performance = mock(PortfolioYtdTwrReader.class);
    when(performance.ytdTwr(7L))
        .thenReturn(ReturnMetric.unavailable(ReturnMetric.Status.INSUFFICIENT_DATA, "missing"));
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);

    var result =
        new RetirementScenarioObservationService(longTerm, current, bonds, performance, clock)
            .load(
                7L,
                new PlanningTimeline(
                    List.of(
                        closedYear(2025, "165"),
                        new PlanningTimelineYear(
                            2026,
                            43,
                            PlanningTimelineState.LIVE,
                            null,
                            new CurrentPlanningYear(
                                2026,
                                null,
                                null,
                                Map.of(
                                    PlanningMetric.CORE_SPENDING, value("100"),
                                    PlanningMetric.DISCRETIONARY_SPENDING, value("20")),
                                Map.of()),
                            null))));

    assertEquals(
        ScenarioObservationAvailability.INSUFFICIENT_HISTORY,
        result.get("Spending growth").availability());
    assertEquals(
        ScenarioObservationAvailability.INSUFFICIENT_HISTORY,
        result.get("Equity return").availability());
  }

  @Test
  void validZeroYtdReturnRemainsAvailable() {
    var performance = mock(PortfolioYtdTwrReader.class);
    when(performance.ytdTwr(7L)).thenReturn(ReturnMetric.available(BigDecimal.ZERO));
    var service =
        new RetirementScenarioObservationService(
            mock(LongTermAssetAnnualSnapshotReader.class),
            mock(LongTermAssetProfileReader.class),
            mock(BondReturnObservationReader.class),
            performance,
            Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC));

    var result =
        service.load(
            7L,
            new PlanningTimeline(
                List.of(
                    new PlanningTimelineYear(
                        2026, 43, PlanningTimelineState.LIVE, null, null, null))));

    assertEquals(
        ScenarioObservationAvailability.AVAILABLE, result.get("Equity return").availability());
    assertEquals(BigDecimal.ZERO, result.get("Equity return").value());
  }

  @Test
  void conversionFailureAndInsufficientSpendingHistoryRemainExplicit() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader current = mock(LongTermAssetProfileReader.class);
    BondReturnObservationReader bonds = mock(BondReturnObservationReader.class);
    PortfolioYtdTwrReader performance = mock(PortfolioYtdTwrReader.class);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenThrow(new CurrencyConversionUnavailableException("EUR/PLN missing"));
    when(longTerm.historicalAnnualSnapshot(7L, 2024))
        .thenThrow(new CurrencyConversionUnavailableException("EUR/PLN missing"));

    var result =
        new RetirementScenarioObservationService(
                longTerm,
                current,
                bonds,
                performance,
                Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC))
            .load(7L, new PlanningTimeline(List.of(closedYear(2025, "0"))));

    assertEquals(
        ScenarioObservationAvailability.INSUFFICIENT_HISTORY,
        result.get("Spending growth").availability());
    assertEquals(
        ScenarioObservationAvailability.INSUFFICIENT_HISTORY,
        result.get("Rental growth").availability());
    assertEquals(
        ScenarioObservationAvailability.UNAVAILABLE, result.get("Equity return").availability());
  }

  @Test
  void historicalObservationUsesCompletedYearsInsteadOfFutureYearEnd() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    LongTermAssetProfileReader current = mock(LongTermAssetProfileReader.class);
    BondReturnObservationReader bonds = mock(BondReturnObservationReader.class);
    PortfolioYtdTwrReader performance = mock(PortfolioYtdTwrReader.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    var currentSnapshot =
        snapshot(
            new LongTermAssetAnnualSnapshotModel(null, BigDecimal.ONE, null, null, null, null));
    when(current.snapshot(7L, java.time.LocalDate.of(2026, 8, 29))).thenReturn(currentSnapshot);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(null, BigDecimal.ONE, null, null, null, null));

    var result =
        new RetirementScenarioObservationService(longTerm, current, bonds, performance, clock)
            .load(7L, new PlanningTimeline(java.util.List.of()));

    assertEquals(
        com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability.AVAILABLE,
        result.get("Rental growth").availability());
    assertEquals(BigDecimal.ZERO, result.get("Rental growth").value());
    verify(longTerm).historicalAnnualSnapshot(7L, 2025);
    verify(longTerm, never()).historicalAnnualSnapshot(7L, 2026);
    verify(performance, never()).ytdTwr(7L);
  }

  private static PlanningTimelineYear closedYear(int year, String spending) {
    var values =
        Map.of(
            PlanningMetric.CORE_SPENDING,
            value(spending),
            PlanningMetric.DISCRETIONARY_SPENDING,
            value("0"));
    return new PlanningTimelineYear(
        year,
        40 + year - 2023,
        PlanningTimelineState.ACTUAL,
        new PastPlanningYear(year, PlanningYearStatus.CLOSED, null, values, Map.of()),
        null,
        null);
  }

  private static com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel
      snapshot(LongTermAssetAnnualSnapshotModel annual) {
    var snapshot =
        mock(com.smartbox.investory.longterm.api.model.LongTermAssetProfileSnapshotModel.class);
    when(snapshot.annualSnapshot()).thenReturn(annual);
    return snapshot;
  }

  private static PlanningMetricValue value(String amount) {
    return new PlanningMetricValue(
        PlanningMetric.CORE_SPENDING,
        new BigDecimal(amount),
        null,
        PlanningValueSource.USER_ENTERED,
        null);
  }
}
