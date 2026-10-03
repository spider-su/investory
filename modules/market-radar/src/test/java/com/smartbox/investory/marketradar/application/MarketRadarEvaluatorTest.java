package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.RadarOutcome;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.RadarOutcomeStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketRadarEvaluatorTest {

  @Test
  void storesSymbolAndBenchmarkReturnForMatureSignal() {
    LocalDate signalDate = LocalDate.of(2026, 1, 2);
    RadarSnapshot signal =
        new RadarSnapshot(
            "ABC", signalDate, RadarState.EMERGING, 100, null, null, null, null, null, List.of());
    CapturingStore store = new CapturingStore(signal);
    HistoricalMarketDataPort marketData =
        (symbol, from, to) ->
            "SPY".equals(symbol)
                ? List.of(bar(signalDate, 200), bar(signalDate.plusDays(7), 202))
                : List.of(bar(signalDate.plusDays(7), 110));

    int evaluated =
        new MarketRadarEvaluator(marketData, store).evaluate(signalDate.plusDays(8), "SPY");

    assertThat(evaluated).isEqualTo(1);
    assertThat(store.saved).hasSize(1);
    assertThat(store.saved.getFirst().symbolReturn()).isCloseTo(0.10, within(1e-10));
    assertThat(store.saved.getFirst().benchmarkReturn()).isCloseTo(0.01, within(1e-10));
    assertThat(store.saved.getFirst().excessReturn()).isCloseTo(0.09, within(1e-10));
  }

  private DailyMarketBar bar(LocalDate date, double close) {
    return new DailyMarketBar(date, close, close, close, close, 1_000);
  }

  private static class CapturingStore implements RadarOutcomeStore {
    private final RadarSnapshot signal;
    private final List<RadarOutcome> saved = new ArrayList<>();

    private CapturingStore(RadarSnapshot signal) {
      this.signal = signal;
    }

    @Override
    public List<RadarSnapshot> unevaluated(int horizonDays, LocalDate cutoff) {
      return horizonDays == 7 && !signal.date().isAfter(cutoff) ? List.of(signal) : List.of();
    }

    @Override
    public void save(RadarOutcome outcome) {
      saved.add(outcome);
    }

    @Override
    public List<RadarOutcome> outcomes(String symbol, int limit) {
      return List.copyOf(saved);
    }
  }
}
