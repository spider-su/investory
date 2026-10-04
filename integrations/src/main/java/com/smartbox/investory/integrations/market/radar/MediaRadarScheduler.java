package com.smartbox.investory.integrations.market.radar;

import com.smartbox.investory.marketradar.api.MediaRadarApi;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import com.smartbox.investory.marketradar.port.MediaSourcePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.market-radar.media.enabled", havingValue = "true")
public class MediaRadarScheduler {
  private final MediaRadarApi media;
  private final List<MediaSourcePort> sources;
  private final Clock clock;
  private final List<String> symbols;

  public MediaRadarScheduler(
      MediaRadarApi media,
      List<MediaSourcePort> sources,
      Clock clock,
      @Value(
              "${app.market-radar.media.symbols:NVDA,MSFT,AAPL,GOOGL,AMZN,META,TSLA,ASML,TSM,AMD,AVGO,UNH,NKE,PFE,LLY,JPM,XOM,CVX,PLTR,CRWD}")
          String symbols) {
    this.media = media;
    this.sources = List.copyOf(sources);
    this.clock = clock;
    this.symbols =
        Arrays.stream(symbols.split(","))
            .map(String::trim)
            .filter(symbol -> !symbol.isBlank())
            .map(symbol -> symbol.toUpperCase(java.util.Locale.ROOT))
            .distinct()
            .toList();
  }

  @Scheduled(cron = "${app.market-radar.media.cron:0 10 23 * * 1-5}", zone = "Europe/Warsaw")
  public void refresh() {
    Instant since = Instant.now(clock).minus(Duration.ofDays(2));
    for (MediaSourcePort source : sources) {
      for (MediaObservation observation : source.fetch(symbols, since)) {
        media.ingest(observation);
      }
    }
  }
}
