package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.MediaRadarApi;
import com.smartbox.investory.marketradar.domain.MediaAttentionSignal;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import com.smartbox.investory.marketradar.port.MediaObservationStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MediaRadarService implements MediaRadarApi {
  private final MediaObservationStore store;
  private final Clock clock;

  public MediaRadarService(MediaObservationStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  @Override
  public void ingest(MediaObservation observation) {
    store.save(observation);
  }

  @Override
  public List<MediaAttentionSignal> attentionSignals(int limit) {
    return store.attentionSignals(Instant.now(clock), Math.max(1, Math.min(limit, 200)));
  }

  @Override
  public List<MediaObservation> recentMentions(String symbol, int limit) {
    return store.recent(symbol, Math.max(1, Math.min(limit, 200)));
  }
}
