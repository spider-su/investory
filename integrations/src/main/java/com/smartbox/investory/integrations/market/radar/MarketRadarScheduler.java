package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.MarketRadarEvaluator;
import com.smartbox.investory.marketradar.application.MarketRadarScanner;
import com.smartbox.investory.marketradar.application.RadarScanResult;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.market-radar.enabled", havingValue = "true")
public class MarketRadarScheduler {
  private static final System.Logger LOGGER =
      System.getLogger(MarketRadarScheduler.class.getName());

  private final MarketRadarScanner scanner;
  private final MarketRadarEvaluator evaluator;
  private final Clock clock;
  private final List<String> symbols;
  private final String benchmark;
  private final int batchSize;
  private final long batchPauseMs;

  public MarketRadarScheduler(
      MarketRadarScanner scanner,
      MarketRadarEvaluator evaluator,
      Clock clock,
      @Value("${app.market-radar.symbols:}") String configuredSymbols,
      @Value("${app.market-radar.benchmark:SPY}") String benchmark,
      @Value("${app.market-radar.batch-size:25}") int batchSize,
      @Value("${app.market-radar.batch-pause-ms:5000}") long batchPauseMs) {
    this.scanner = scanner;
    this.evaluator = evaluator;
    this.clock = clock;
    this.symbols =
        configuredSymbols == null || configuredSymbols.isBlank()
            ? loadDefaultUniverse()
            : parseSymbols(configuredSymbols);
    this.benchmark = benchmark.trim();
    this.batchSize = Math.max(1, batchSize);
    this.batchPauseMs = Math.max(0, batchPauseMs);
  }

  @Scheduled(cron = "${app.market-radar.cron:0 30 22 * * 1-5}", zone = "Europe/Warsaw")
  public void refresh() {
    Instant startedAt = clock.instant();
    int attempted = 0;
    int stored = 0;
    int noData = 0;
    int failed = 0;
    Map<RadarState, Integer> states = new EnumMap<>(RadarState.class);

    for (int start = 0; start < symbols.size(); start += batchSize) {
      int end = Math.min(start + batchSize, symbols.size());
      RadarScanResult result = scanner.refresh(symbols.subList(start, end));
      attempted += result.attempted();
      stored += result.stored();
      noData += result.noData();
      failed += result.failed();
      countStates(states, result.snapshots());
      pauseBetweenBatches(end < symbols.size());
    }

    int outcomesEvaluated = evaluator.evaluate(LocalDate.now(clock), benchmark);
    long durationMs = Duration.between(startedAt, clock.instant()).toMillis();
    int interesting =
        states.entrySet().stream()
            .filter(entry -> entry.getKey() != RadarState.NORMAL)
            .mapToInt(Map.Entry::getValue)
            .sum();

    LOGGER.log(
        System.Logger.Level.INFO,
        "Market Radar refresh completed"
            + " universe="
            + symbols.size()
            + " attempted="
            + attempted
            + " stored="
            + stored
            + " noData="
            + noData
            + " failed="
            + failed
            + " interesting="
            + interesting
            + " states="
            + states
            + " outcomesEvaluated="
            + outcomesEvaluated
            + " durationMs="
            + durationMs);
  }

  private void countStates(Map<RadarState, Integer> states, List<RadarSnapshot> snapshots) {
    for (RadarSnapshot snapshot : snapshots) {
      states.merge(snapshot.state(), 1, Integer::sum);
    }
  }

  private List<String> parseSymbols(String value) {
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(symbol -> !symbol.isBlank())
        .map(symbol -> symbol.toUpperCase(java.util.Locale.ROOT))
        .distinct()
        .toList();
  }

  private List<String> loadDefaultUniverse() {
    ClassPathResource resource = new ClassPathResource("market-radar/default-universe.txt");
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(symbol -> symbol.toUpperCase(java.util.Locale.ROOT))
          .distinct()
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load default Market Radar universe", e);
    }
  }

  private void pauseBetweenBatches(boolean moreBatches) {
    if (!moreBatches || batchPauseMs == 0) return;
    try {
      Thread.sleep(batchPauseMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Market Radar refresh interrupted", e);
    }
  }
}
