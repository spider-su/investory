package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.MediaObservation;
import java.time.Instant;
import java.util.List;

public interface MediaSourcePort {
  List<MediaObservation> fetch(List<String> symbols, Instant since);
}
