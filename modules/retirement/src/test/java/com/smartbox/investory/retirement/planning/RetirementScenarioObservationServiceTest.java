package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.TrailingPortfolioReturnReader;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetirementScenarioObservationServiceTest {

  @Test
  void spendingGrowthUsesAnnualizedChangeAcrossLastTwoClosedYears() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    TrailingPortfolioReturnReader performance = mock(TrailingPortfolioReturnReader.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));
    when(longTerm.historicalAnnualSnapshot(7L, 2024))
        .thenReturn(new LongTermAssetAnnualSnapshotModel(null, null, null, null, null, null));
    when(performance.returnPercentage(7L, YearMonth.of(2025, 8), YearMonth.of(2026, 7)))
        .thenReturn(new BigDecimal("8.5"));

    var result =
        new RetirementScenarioObservationService(longTerm, performance, clock)
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
    assertEquals(new BigDecimal("0.085"), result.get("Equity return").value());
  }

  @Test
  void conversionFailureAndInsufficientSpendingHistoryRemainExplicit() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    TrailingPortfolioReturnReader performance = mock(TrailingPortfolioReturnReader.class);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenThrow(new CurrencyConversionUnavailableException("EUR/PLN missing"));
    when(longTerm.historicalAnnualSnapshot(7L, 2024))
        .thenThrow(new CurrencyConversionUnavailableException("EUR/PLN missing"));
    when(performance.returnPercentage(7L, YearMonth.of(2025, 8), YearMonth.of(2026, 7)))
        .thenReturn(null);

    var result =
        new RetirementScenarioObservationService(
                longTerm,
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
    TrailingPortfolioReturnReader performance = mock(TrailingPortfolioReturnReader.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(null, BigDecimal.ONE, null, null, null, null));
    when(longTerm.historicalAnnualSnapshot(7L, 2024))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(null, BigDecimal.ONE, null, null, null, null));
    when(performance.returnPercentage(7L, YearMonth.of(2025, 8), YearMonth.of(2026, 7)))
        .thenReturn(null);

    var result =
        new RetirementScenarioObservationService(longTerm, performance, clock)
            .load(7L, new PlanningTimeline(java.util.List.of()));

    assertEquals(
        com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability.AVAILABLE,
        result.get("Rental growth").availability());
    assertEquals(BigDecimal.ZERO, result.get("Rental growth").value());
    verify(longTerm).historicalAnnualSnapshot(7L, 2025);
    verify(longTerm).historicalAnnualSnapshot(7L, 2024);
    verify(longTerm, never()).historicalAnnualSnapshot(7L, 2026);
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

  private static PlanningMetricValue value(String amount) {
    return new PlanningMetricValue(
        PlanningMetric.CORE_SPENDING,
        new BigDecimal(amount),
        null,
        PlanningValueSource.USER_ENTERED,
        null);
  }
}
