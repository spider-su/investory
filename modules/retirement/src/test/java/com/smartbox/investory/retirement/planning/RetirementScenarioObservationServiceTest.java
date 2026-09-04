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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RetirementScenarioObservationServiceTest {

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
    when(performance.returnPercentage(7L, YearMonth.of(2025, 9), YearMonth.of(2026, 8)))
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
}
