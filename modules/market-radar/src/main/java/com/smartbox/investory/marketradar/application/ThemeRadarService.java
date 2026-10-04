package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.ThemeRadarApi;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.ThemeDefinition;
import com.smartbox.investory.marketradar.domain.ThemeSnapshot;
import com.smartbox.investory.marketradar.domain.ThemeState;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.ThemeSnapshotStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ThemeRadarService implements ThemeRadarApi {
  private final HistoricalMarketDataPort marketData;
  private final ThemeSnapshotStore store;

  public ThemeRadarService(HistoricalMarketDataPort marketData, ThemeSnapshotStore store) {
    this.marketData = marketData;
    this.store = store;
  }

  public Optional<ThemeSnapshot> analyze(
      ThemeDefinition definition, String benchmark, LocalDate date) {
    Optional<SeriesMetrics> proxy = metrics(definition.proxySymbol(), date);
    Optional<SeriesMetrics> benchmarkMetrics = metrics(benchmark, date);
    if (proxy.isEmpty() || benchmarkMetrics.isEmpty()) return Optional.empty();

    int available = 0;
    int aboveSma50 = 0;
    int outperforming = 0;
    double benchmarkReturn = benchmarkMetrics.orElseThrow().return20d();
    for (String member : definition.members()) {
      Optional<SeriesMetrics> metrics = metrics(member, date);
      if (metrics.isEmpty()) continue;
      available++;
      if (metrics.orElseThrow().aboveSma50()) aboveSma50++;
      if (metrics.orElseThrow().return20d() > benchmarkReturn) outperforming++;
    }
    if (available == 0) return Optional.empty();

    double breadthAbove = (double) aboveSma50 / available;
    double breadthOutperforming = (double) outperforming / available;
    double proxyReturn = proxy.orElseThrow().return20d();
    double relativeStrength = proxyReturn - benchmarkReturn;
    ThemeState state = classify(breadthAbove, breadthOutperforming, relativeStrength);

    return Optional.of(
        new ThemeSnapshot(
            definition.name(),
            definition.proxySymbol(),
            date,
            state,
            available,
            breadthAbove,
            breadthOutperforming,
            proxyReturn,
            benchmarkReturn,
            relativeStrength));
  }

  public List<ThemeSnapshot> refresh(
      List<ThemeDefinition> definitions, String benchmark, LocalDate date) {
    List<ThemeSnapshot> result = new ArrayList<>();
    for (ThemeDefinition definition : definitions) {
      analyze(definition, benchmark, date)
          .ifPresent(
              snapshot -> {
                store.save(snapshot);
                result.add(snapshot);
              });
    }
    return List.copyOf(result);
  }

  @Override
  public List<ThemeSnapshot> latestThemes() {
    return store.latest();
  }

  @Override
  public List<ThemeSnapshot> themeHistory(String theme, int limit) {
    return store.history(theme, Math.max(1, Math.min(limit, 180)));
  }

  private Optional<SeriesMetrics> metrics(String symbol, LocalDate date) {
    List<DailyMarketBar> bars =
        marketData.dailyBars(symbol, date.minusDays(100), date).stream()
            .sorted(Comparator.comparing(DailyMarketBar::date))
            .toList();
    if (bars.size() < 51) return Optional.empty();
    int last = bars.size() - 1;
    double close = bars.get(last).close();
    double return20 = close / bars.get(last - 20).close() - 1.0;
    double sma50 =
        bars.subList(last - 49, last + 1).stream()
            .mapToDouble(DailyMarketBar::close)
            .average()
            .orElse(close);
    return Optional.of(new SeriesMetrics(return20, close > sma50));
  }

  private ThemeState classify(double breadthAbove, double breadthOutperforming, double relativeStrength) {
    if (breadthAbove >= 0.70 && breadthOutperforming >= 0.60 && relativeStrength >= 0.03) {
      return ThemeState.ACCELERATING;
    }
    if (breadthAbove >= 0.60 && breadthOutperforming >= 0.50 && relativeStrength > 0) {
      return ThemeState.STRONG;
    }
    if (breadthAbove < 0.40 && breadthOutperforming < 0.40 && relativeStrength < 0) {
      return ThemeState.WEAKENING;
    }
    return ThemeState.NEUTRAL;
  }

  private record SeriesMetrics(double return20d, boolean aboveSma50) {}
}
