package com.smartbox.investory.retirement.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.reporting.TrailingPortfolioReturnReader;
import com.smartbox.investory.longterm.api.LongTermAssetAnnualSnapshotReader;
import com.smartbox.investory.longterm.api.model.LongTermAssetAnnualSnapshotModel;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RetirementScenarioObservationServiceTest {

  @Test
  void staleHistoricalFxMakesRentalObservationUnavailableInsteadOfBreakingSimulation() {
    LongTermAssetAnnualSnapshotReader longTerm = mock(LongTermAssetAnnualSnapshotReader.class);
    TrailingPortfolioReturnReader performance = mock(TrailingPortfolioReturnReader.class);
    Clock clock = Clock.fixed(Instant.parse("2026-08-29T00:00:00Z"), ZoneOffset.UTC);
    when(longTerm.historicalAnnualSnapshot(7L, 2026))
        .thenThrow(new CurrencyConversionUnavailableException("stale FX rate"));
    when(longTerm.historicalAnnualSnapshot(7L, 2025))
        .thenReturn(
            new LongTermAssetAnnualSnapshotModel(null, BigDecimal.ONE, null, null, null, null));
    when(performance.returnPercentage(7L, YearMonth.of(2025, 9), YearMonth.of(2026, 8)))
        .thenReturn(null);

    var result =
        new RetirementScenarioObservationService(longTerm, performance, clock)
            .load(7L, new PlanningTimeline(java.util.List.of()));

    assertEquals(
        com.smartbox.investory.retirement.api.model.ScenarioObservationAvailability
            .INSUFFICIENT_HISTORY,
        result.get("Rental growth").availability());
    assertNull(result.get("Rental growth").value());
  }
}
