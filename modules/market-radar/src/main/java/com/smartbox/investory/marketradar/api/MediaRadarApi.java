package com.smartbox.investory.marketradar.api;

import com.smartbox.investory.marketradar.domain.MediaAttentionSignal;
import com.smartbox.investory.marketradar.domain.MediaObservation;
import java.util.List;

public interface MediaRadarApi {
  void ingest(MediaObservation observation);

  List<MediaAttentionSignal> attentionSignals(int limit);

  List<MediaObservation> recentMentions(String symbol, int limit);
}
