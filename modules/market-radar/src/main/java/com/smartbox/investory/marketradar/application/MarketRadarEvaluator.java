package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.DailyMarketBar;
import com.smartbox.investory.marketradar.domain.RadarOutcome;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.port.HistoricalMarketDataPort;
import com.smartbox.investory.marketradar.port.RadarOutcomeStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarketRadarEvaluator {
  private static final List<Integer> HORIZONS = List.of(7, 30, 90);

  private final HistoricalMarketDataPort marketData;
  private final RadarOutcomeStore outcomes;

  public MarketRadarEvaluator(HistoricalMarketDataPort marketData, RadarOutcomeStore outcomes) {
    this.marketData = marketData;
    this.outcomes = outcomes;
  }

  public int evaluate(LocalDate today, String benchmark) {
    int evaluated = 0;
    for (int horizon : HORIZONS) {
      for (RadarSnapshot signal : outcomes.unevaluated(horizon, today.minusDays(horizon))) {
        Optional<RadarOutcome> outcome = evaluate(signal, horizon, benchmark, today);
        if (outcome.isPresent()) {
          outcomes.save(outcome.orElseThrow());
          evaluated++;
        }
      }
    }
    return evaluated;
  }

  private Optional<RadarOutcome> evaluate(
      RadarSnapshot signal, int horizon, String benchmark, LocalDate today) {
    LocalDate target = signal.date().plusDays(horizon);
    Optional<DailyMarketBar> symbolBar = firstBarOnOrAfter(signal.symbol(), target, today);
    if (symbolBar.isEmpty()) return Optional.empty();

    double symbolReturn = change(symbolBar.orElseThrow().close(), signal.close());
    Optional<Double> benchmarkReturn = benchmarkReturn(benchmark, signal.date(), target, today);
    Double excess = benchmarkReturn.map(value -> symbolReturn - value).orElse(null);
    return Optional.of(
        new RadarOutcome(
            signal.symbol(),
            signal.date(),
            horizon,
            symbolBar.orElseThrow().date(),
            symbolReturn,
            benchmark,
            benchmarkReturn.orElse(null),
            excess));
  }

  private Optional<Double> benchmarkReturn(
      String benchmark, LocalDate signalDate, LocalDate target, LocalDate today) {
    List<DailyMarketBar> bars = marketData.dailyBars(benchmark, signalDate.minusDays(5), today);
    Optional<DailyMarketBar> start =
        bars.stream().filter(bar -> !bar.date().isBefore(signalDate)).findFirst();
    Optional<DailyMarketBar> end =
        bars.stream().filter(bar -> !bar.date().isBefore(target)).findFirst();
    if (start.isEmpty() || end.isEmpty()) return Optional.empty();
    return Optional.of(change(end.orElseThrow().close(), start.orElseThrow().close()));
  }

  private Optional<DailyMarketBar> firstBarOnOrAfter(
      String symbol, LocalDate target, LocalDate today) {
    return marketData.dailyBars(symbol, target, today).stream()
        .filter(bar -> !bar.date().isBefore(target))
        .findFirst();
  }

  private double change(double current, double previous) {
    return previous == 0 ? 0 : current / previous - 1.0;
  }
}
