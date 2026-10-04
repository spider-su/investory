package com.smartbox.investory.integrations.market.radar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.marketradar.application.MarketRadarEvaluator;
import com.smartbox.investory.marketradar.application.MarketRadarScanner;
import com.smartbox.investory.marketradar.application.RadarScanResult;
import com.smartbox.investory.marketradar.domain.RadarRunStatus;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.port.RadarRunStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketRadarSchedulerTest {

  @Test
  void persistsStartedAndSuccessfulRun() {
    MarketRadarScanner scanner = mock(MarketRadarScanner.class);
    MarketRadarEvaluator evaluator = mock(MarketRadarEvaluator.class);
    RadarRunStore runs = mock(RadarRunStore.class);
    RadarSnapshot snapshot =
        new RadarSnapshot(
            "AAPL",
            LocalDate.of(2026, 10, 2),
            RadarState.EMERGING,
            100,
            0.03,
            0.08,
            1.6,
            0.02,
            60.0,
            List.of("Relative volume is elevated"));
    when(scanner.refresh(any())).thenReturn(new RadarScanResult(2, 1, 1, 0, List.of(snapshot)));
    when(evaluator.evaluate(any(), any())).thenReturn(3);

    MarketRadarScheduler scheduler =
        new MarketRadarScheduler(
            scanner,
            evaluator,
            runs,
            Clock.fixed(Instant.parse("2026-10-04T20:30:00Z"), ZoneOffset.UTC),
            "AAPL,MSFT",
            "SPY",
            25,
            0);

    scheduler.refresh();

    ArgumentCaptor<RadarRunSummary> captor = ArgumentCaptor.forClass(RadarRunSummary.class);
    verify(runs, times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).status()).isEqualTo(RadarRunStatus.STARTED);
    RadarRunSummary completed = captor.getAllValues().get(1);
    assertThat(completed.status()).isEqualTo(RadarRunStatus.SUCCESS);
    assertThat(completed.attempted()).isEqualTo(2);
    assertThat(completed.stored()).isEqualTo(1);
    assertThat(completed.noData()).isEqualTo(1);
    assertThat(completed.interesting()).isEqualTo(1);
    assertThat(completed.emerging()).isEqualTo(1);
    assertThat(completed.outcomesEvaluated()).isEqualTo(3);
  }
}
