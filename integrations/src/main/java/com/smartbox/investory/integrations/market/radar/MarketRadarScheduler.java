package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.application.MarketRadarScanner;
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
  private final List<String> symbols;

  public MarketRadarScheduler(
      MarketRadarScanner scanner,
      @Value("${app.market-radar.symbols:SPY,QQQ,AAPL,MSFT,NVDA,GOOGL,AMZN,META,TSM,ASML}") String symbols) {
    this.scanner = scanner;
    this.symbols = Arrays.stream(symbols.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  @Scheduled(cron = "${app.market-radar.cron:0 30 22 * * 1-5}", zone = "Europe/Warsaw")
  public void refresh() {
    scanner.refresh(symbols);
  }
}
