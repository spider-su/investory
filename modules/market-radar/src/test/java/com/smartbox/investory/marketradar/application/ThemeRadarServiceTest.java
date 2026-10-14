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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  @Test
  void refreshReusesOverlappingSymbolsOnceAndPreservesSnapshotsAndOrder() {
    LocalDate date = LocalDate.of(2026, 10, 2);
    Map<String, Integer> requests = new HashMap<>();
    HistoricalMarketDataPort marketData =
        (symbol, from, to) -> {
          assertThat(from).isEqualTo(date.minusDays(100));
          assertThat(to).isEqualTo(date);
          requests.merge(symbol, 1, Integer::sum);
          return switch (symbol) {
            case "SPY" -> bars(date, 100, 0.05);
            case "SMH" -> bars(date, 100, 0.20);
            case "CLOUD" -> bars(date, 100, -0.10);
            case "MSFT" -> bars(date, 100, -0.20);
            default -> bars(date, 100, 0.18);
          };
        };
    CapturingStore store = new CapturingStore();
    ThemeRadarService service = new ThemeRadarService(marketData, store);
    List<ThemeDefinition> definitions =
        List.of(
            new ThemeDefinition("Semiconductors", "SMH", List.of("NVDA", "AMD")),
            new ThemeDefinition("Cloud", "CLOUD", List.of("NVDA", "MSFT")));

    List<ThemeSnapshot> result = service.refresh(definitions, "SPY", date);

    assertThat(requests)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("SPY", 1, "SMH", 1, "CLOUD", 1, "NVDA", 1, "AMD", 1, "MSFT", 1));
    assertThat(result).containsExactlyElementsOf(store.saved);
    assertThat(result).extracting(ThemeSnapshot::theme).containsExactly("Semiconductors", "Cloud");
    assertThat(result)
        .extracting(ThemeSnapshot::state)
        .containsExactly(ThemeState.ACCELERATING, ThemeState.NEUTRAL);
    assertThat(result).allSatisfy(snapshot -> assertThat(snapshot.date()).isEqualTo(date));
    assertThat(result.getFirst().breadthAboveSma50()).isEqualTo(1.0);
    assertThat(result.getFirst().breadthOutperformingBenchmark()).isEqualTo(1.0);
    double expectedProxyReturn =
        bars(date, 100, 0.20).getLast().close() / bars(date, 100, 0.20).get(49).close() - 1.0;
    double expectedBenchmarkReturn =
        bars(date, 100, 0.05).getLast().close() / bars(date, 100, 0.05).get(49).close() - 1.0;
    assertThat(result.getFirst().proxyReturn20d()).isEqualTo(expectedProxyReturn);
    assertThat(result.getFirst().benchmarkReturn20d()).isEqualTo(expectedBenchmarkReturn);
    assertThat(result.getFirst().relativeStrength20d())
        .isEqualTo(expectedProxyReturn - expectedBenchmarkReturn);
  }

  @Test
  void failedFetchIsMemoizedOnlyForItsExactSymbolAndDateWithinRefresh() {
    LocalDate date = LocalDate.of(2026, 10, 2);
    Map<String, Integer> requests = new HashMap<>();
    HistoricalMarketDataPort marketData =
        (symbol, from, to) -> {
          requests.merge(symbol, 1, Integer::sum);
          if (symbol.equals("SHARED")) return List.of();
          return bars(date, 100, symbol.equals("SPY") ? 0.05 : 0.20);
        };
    ThemeRadarService service = new ThemeRadarService(marketData, new CapturingStore());

    List<ThemeSnapshot> result =
        service.refresh(
            List.of(
                new ThemeDefinition("First", "SHARED", List.of("GOOD")),
                new ThemeDefinition("Second", "SHARED", List.of("GOOD"))),
            "SPY",
            date);

    assertThat(result).isEmpty();
    assertThat(requests).containsExactlyInAnyOrderEntriesOf(Map.of("SHARED", 1, "SPY", 1));
  }

  @Test
  void refreshUsesRequestedDateForEachCallAndDoesNotShareAcrossInvocations() {
    LocalDate firstDate = LocalDate.of(2026, 10, 2);
    LocalDate secondDate = firstDate.plusDays(1);
    List<String> windows = new ArrayList<>();
    HistoricalMarketDataPort marketData =
        (symbol, from, to) -> {
          windows.add(symbol + ":" + from + ":" + to);
          return bars(to, 100, symbol.equals("SPY") ? 0.05 : 0.20);
        };
    ThemeRadarService service = new ThemeRadarService(marketData, new CapturingStore());
    ThemeDefinition definition = new ThemeDefinition("Theme", "PROXY", List.of("MEMBER"));

    service.refresh(List.of(definition), "SPY", firstDate);
    service.refresh(List.of(definition), "SPY", secondDate);

    assertThat(windows)
        .containsExactly(
            "PROXY:2026-06-24:2026-10-02",
            "SPY:2026-06-24:2026-10-02",
            "MEMBER:2026-06-24:2026-10-02",
            "PROXY:2026-06-25:2026-10-03",
            "SPY:2026-06-25:2026-10-03",
            "MEMBER:2026-06-25:2026-10-03");
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
    private final List<ThemeSnapshot> saved = new ArrayList<>();

    @Override
    public void save(ThemeSnapshot snapshot) {
      saved.add(snapshot);
    }

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
