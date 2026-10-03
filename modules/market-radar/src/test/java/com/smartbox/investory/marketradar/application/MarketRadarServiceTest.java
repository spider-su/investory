package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketRadarServiceTest {

  @Test
  void analyzesAvailableHistoryThroughThePort() {
    HistoricalMarketDataPort port = (symbol, from, to) -> bars();
    MarketRadarService service =
        new MarketRadarService(
            port,
            new MarketSignalCalculator(),
            Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC));

    var result = service.analyze("abc");

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().symbol()).isEqualTo("ABC");
  }

  private List<DailyMarketBar> bars() {
    List<DailyMarketBar> result = new ArrayList<>();
    for (int i = 0; i < 80; i++) {
      double close = 100 + i * 0.1;
      result.add(
          new DailyMarketBar(
              LocalDate.of(2026, 6, 1).plusDays(i), close, close, close, close, 1_000));
    }
    return result;
  }
}
