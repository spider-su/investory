package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;\nimport com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarketRadarService implements MarketRadarApi {

  private final HistoricalMarketDataPort marketData;\n  private final RadarSnapshotStore store;
  private final MarketSignalCalculator calculator;
  private final Clock clock;

  public MarketRadarService(
      HistoricalMarketDataPort marketData,\n      RadarSnapshotStore store,\n      MarketSignalCalculator calculator,\n      Clock clock) {
    this.marketData = marketData;\n    this.store = store;
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
  public List<RadarSnapshot> latestSignals() {
    return store.latest();
  }
}

