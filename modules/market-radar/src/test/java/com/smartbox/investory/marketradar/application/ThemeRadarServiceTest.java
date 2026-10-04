package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.ThemeDefinition;
import com.smartbox.investory.marketradar.domain.ThemeSnapshot;
import com.smartbox.investory.marketradar.domain.ThemeState;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.ThemeSnapshotStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThemeRadarServiceTest {

  @Test
  void classifiesBroadStrongThemeAsAccelerating() {
    LocalDate date = LocalDate.of(2026, 10, 2);
    HistoricalMarketDataPort marketData =
        (symbol, from, to) ->
            switch (symbol) {
              case "SPY" -> bars(date, 100, 0.05);
              case "SMH" -> bars(date, 100, 0.20);
              default -> bars(date, 100, 0.18);
            };
    CapturingStore store = new CapturingStore();
    ThemeRadarService service = new ThemeRadarService(marketData, store);

    var result =
        service.analyze(
            new ThemeDefinition("Semiconductors", "SMH", List.of("NVDA", "AMD", "AVGO")),
            "SPY",
            date);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().state()).isEqualTo(ThemeState.ACCELERATING);
    assertThat(result.orElseThrow().breadthAboveSma50()).isEqualTo(1.0);
    assertThat(result.orElseThrow().breadthOutperformingBenchmark()).isEqualTo(1.0);
  }

  private List<DailyMarketBar> bars(LocalDate end, double start, double totalReturn) {
    List<DailyMarketBar> result = new ArrayList<>();
    for (int i = 0; i < 70; i++) {
      double close = start * (1.0 + totalReturn * i / 69.0);
      result.add(new DailyMarketBar(end.minusDays(69 - i), close, close, close, close, 1_000));
    }
    return result;
  }

  private static class CapturingStore implements ThemeSnapshotStore {
    @Override
    public void save(ThemeSnapshot snapshot) {}

    @Override
    public List<ThemeSnapshot> latest() {
      return List.of();
    }

    @Override
    public List<ThemeSnapshot> history(String theme, int limit) {
      return List.of();
    }
  }
}
