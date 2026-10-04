package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.MediaAttentionSignal;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import java.time.Instant;
import java.util.List;

public interface MediaObservationStore {
  void save(MediaObservation observation);

  List<MediaAttentionSignal> attentionSignals(Instant now, int limit);

  List<MediaObservation> recent(String symbol, int limit);
}
