package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.marketradar.domain.RadarSignal;
import com.smartbox.investory.marketradar.domain.RadarSignalType;
import com.smartbox.investory.marketradar.port.MarketSignalSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketRadarServiceTest {

  @Test
  void combinesSourcesAndReturnsNewestSignalFirst() {
    RadarSignal older =
        new RadarSignal(
            "asml", RadarSignalType.MEDIA_CONSENSUS, "Media interest rising", Instant.parse("2026-10-01T10:00:00Z"));
    RadarSignal newer =
        new RadarSignal(
            "nvda", RadarSignalType.PRICE_VOLUME, "Relative volume elevated", Instant.parse("2026-10-02T10:00:00Z"));
    MarketSignalSource first = () -> List.of(older);
    MarketSignalSource second = () -> List.of(newer);

    MarketRadarService service = new MarketRadarService(List.of(first, second));

    assertThat(service.currentSignals()).containsExactly(newer, older);
  }
}
