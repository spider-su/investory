package com.smartbox.investory.marketradar.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.marketradar.domain.MediaAttentionSignal;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import com.smartbox.investory.marketradar.domain.MediaSourceType;
import com.smartbox.investory.marketradar.domain.OpinionStance;
import com.smartbox.investory.marketradar.port.MediaObservationStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaRadarServiceTest {

  @Test
  void persistsExplicitAnalystObservationWithoutInferringStance() {
    CapturingStore store = new CapturingStore();
    MediaRadarService service =
        new MediaRadarService(
            store,
            Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC));
    MediaObservation observation =
        new MediaObservation(
            "id-1",
            MediaSourceType.ANALYST,
            "Example Research",
            "Analyst",
            "NVDA outlook",
            null,
            Instant.parse("2026-10-04T09:00:00Z"),
            List.of("NVDA"),
            OpinionStance.BULLISH);

    service.ingest(observation);

    assertThat(store.saved).containsExactly(observation);
  }

  private static class CapturingStore implements MediaObservationStore {
    private final List<MediaObservation> saved = new ArrayList<>();

    @Override
    public void save(MediaObservation observation) {
      saved.add(observation);
    }

    @Override
    public List<MediaAttentionSignal> attentionSignals(Instant now, int limit) {
      return List.of();
    }

    @Override
    public List<MediaObservation> recent(String symbol, int limit) {
      return List.copyOf(saved);
    }
  }
}
