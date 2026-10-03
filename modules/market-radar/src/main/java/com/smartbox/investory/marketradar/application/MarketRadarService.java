package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarketRadarService implements MarketRadarApi {

  private final HistoricalMarketDataPort marketData;
  private final RadarSnapshotStore store;
  private final MarketSignalCalculator calculator;
  private final Clock clock;

  public MarketRadarService(
      HistoricalMarketDataPort marketData,
      RadarSnapshotStore store,
      MarketSignalCalculator calculator,
      Clock clock) {
    this.marketData = marketData;
    this.store = store;
    this.calculator = calculator;
    this.clock = clock;
  }

  @Override
  public Optional<RadarSnapshot> analyze(String symbol) {
    if (symbol == null || symbol.isBlank()) return Optional.empty();
    LocalDate to = LocalDate.now(clock);
    List<DailyMarketBar> bars = marketData.dailyBars(symbol.trim(), to.minusDays(140), to);
    return bars.size() < 61 ? Optional.empty() : Optional.of(calculator.calculate(symbol, bars));
  }

  @Override
  public List<RadarSnapshot> latestSignals() {\n    return store.latest();\n  }\n\n  @Override\n  public List<RadarSnapshot> signalHistory(String symbol, int limit) {\n    if (symbol == null || symbol.isBlank()) return List.of();\n    return store.history(symbol.trim().toUpperCase(java.util.Locale.ROOT), Math.max(1, Math.min(limit, 180)));\n  }
}
