package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.RadarOutcome;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import java.time.LocalDate;
import java.util.List;

public interface RadarOutcomeStore {
  List<RadarSnapshot> unevaluated(int horizonDays, LocalDate cutoff);

  void save(RadarOutcome outcome);

  List<RadarOutcome> outcomes(String symbol, int limit);
}
