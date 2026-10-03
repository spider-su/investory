package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.MarketRadarEvaluator;
import com.smartbox.investory.marketradar.application.MarketRadarScanner;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

  public MarketRadarScheduler(
      MarketRadarScanner scanner,
      MarketRadarEvaluator evaluator,
      Clock clock,
      @Value("${app.market-radar.symbols:SPY,QQQ,AAPL,MSFT,NVDA,GOOGL,AMZN,META,TSM,ASML}")
          String symbols,
      @Value("${app.market-radar.benchmark:SPY}") String benchmark) {
    this.scanner = scanner;
    this.evaluator = evaluator;
    this.clock = clock;
    this.symbols =
        Arrays.stream(symbols.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    this.benchmark = benchmark.trim();
  }

  @Scheduled(cron = "${app.market-radar.cron:0 30 22 * * 1-5}", zone = "Europe/Warsaw")
  public void refresh() {
    scanner.refresh(symbols);
    evaluator.evaluate(LocalDate.now(clock), benchmark);
  }
}
