package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.MarketRadarEvaluator;
import com.smartbox.investory.marketradar.application.MarketRadarScanner;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.market-radar.enabled", havingValue = "true")
public class MarketRadarScheduler {
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
    for (int start = 0; start < symbols.size(); start += batchSize) {
      int end = Math.min(start + batchSize, symbols.size());
      scanner.refresh(symbols.subList(start, end));
      pauseBetweenBatches(end < symbols.size());
    }
    evaluator.evaluate(LocalDate.now(clock), benchmark);
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
      return reader.lines()
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
