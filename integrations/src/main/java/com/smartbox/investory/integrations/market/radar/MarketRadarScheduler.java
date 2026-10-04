package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.MarketRadarEvaluator;
import com.smartbox.investory.marketradar.application.MarketRadarScanner;
import com.smartbox.investory.marketradar.application.RadarScanResult;
import com.smartbox.investory.marketradar.domain.RadarRunStatus;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.port.RadarRunStore;
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
import java.util.UUID;
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
  private final RadarRunStore runs;
  private final Clock clock;
  private final List<String> symbols;
  private final String benchmark;
  private final int batchSize;
  private final long batchPauseMs;

  public MarketRadarScheduler(
      MarketRadarScanner scanner,
      MarketRadarEvaluator evaluator,
      RadarRunStore runs,
      Clock clock,
      @Value("${app.market-radar.symbols:}") String configuredSymbols,
      @Value("${app.market-radar.benchmark:SPY}") String benchmark,
      @Value("${app.market-radar.batch-size:25}") int batchSize,
      @Value("${app.market-radar.batch-pause-ms:5000}") long batchPauseMs) {
    this.scanner = scanner;
    this.evaluator = evaluator;
    this.runs = runs;
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
    UUID runId = UUID.randomUUID();
    Instant startedAt = clock.instant();
    MutableRun run = new MutableRun(runId, startedAt, symbols.size());
    runs.save(run.summary(RadarRunStatus.STARTED, null, startedAt));

    try {
      for (int start = 0; start < symbols.size(); start += batchSize) {
        int end = Math.min(start + batchSize, symbols.size());
        RadarScanResult result = scanner.refresh(symbols.subList(start, end));
        run.add(result);
        pauseBetweenBatches(end < symbols.size());
      }

      run.outcomesEvaluated = evaluator.evaluate(LocalDate.now(clock), benchmark);
      Instant completedAt = clock.instant();
      RadarRunStatus status = run.failed > 0 ? RadarRunStatus.PARTIAL : RadarRunStatus.SUCCESS;
      RadarRunSummary summary = run.summary(status, null, completedAt);
      runs.save(summary);
      log(summary);
    } catch (RuntimeException exception) {
      Instant completedAt = clock.instant();
      RadarRunSummary summary =
          run.summary(RadarRunStatus.FAILED, sanitize(exception.getMessage()), completedAt);
      runs.save(summary);
      log(summary);
      throw exception;
    }
  }

  private void log(RadarRunSummary run) {
    LOGGER.log(
        System.Logger.Level.INFO,
        "Market Radar refresh completed"
            + " status="
            + run.status()
            + " universe="
            + run.universeSize()
            + " attempted="
            + run.attempted()
            + " stored="
            + run.stored()
            + " noData="
            + run.noData()
            + " failed="
            + run.failed()
            + " interesting="
            + run.interesting()
            + " outcomesEvaluated="
            + run.outcomesEvaluated()
            + " durationMs="
            + run.durationMs());
  }

  private String sanitize(String message) {
    if (message == null || message.isBlank()) return "Unexpected refresh failure";
    return message.substring(0, Math.min(message.length(), 500));
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

  private final class MutableRun {
    private final UUID id;
    private final Instant startedAt;
    private final int universeSize;
    private int attempted;
    private int stored;
    private int noData;
    private int failed;
    private int outcomesEvaluated;
    private final Map<RadarState, Integer> states = new EnumMap<>(RadarState.class);

    private MutableRun(UUID id, Instant startedAt, int universeSize) {
      this.id = id;
      this.startedAt = startedAt;
      this.universeSize = universeSize;
    }

    private void add(RadarScanResult result) {
      attempted += result.attempted();
      stored += result.stored();
      noData += result.noData();
      failed += result.failed();
      countStates(states, result.snapshots());
    }

    private RadarRunSummary summary(RadarRunStatus status, String error, Instant completedAt) {
      long durationMs = Math.max(0, Duration.between(startedAt, completedAt).toMillis());
      int interesting =
          states.entrySet().stream()
              .filter(entry -> entry.getKey() != RadarState.NORMAL)
              .mapToInt(Map.Entry::getValue)
              .sum();
      return new RadarRunSummary(
          id,
          startedAt,
          status == RadarRunStatus.STARTED ? null : completedAt,
          status,
          universeSize,
          attempted,
          stored,
          noData,
          failed,
          interesting,
          states.getOrDefault(RadarState.NORMAL, 0),
          states.getOrDefault(RadarState.EMERGING, 0),
          states.getOrDefault(RadarState.TRENDING, 0),
          states.getOrDefault(RadarState.HOT, 0),
          states.getOrDefault(RadarState.EXTENDED, 0),
          states.getOrDefault(RadarState.COOLING, 0),
          outcomesEvaluated,
          status == RadarRunStatus.STARTED ? 0 : durationMs,
          error);
    }
  }
}
