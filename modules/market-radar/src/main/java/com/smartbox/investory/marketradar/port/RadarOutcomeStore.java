package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.RadarOutcome;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarValidationObservation;
import com.smartbox.investory.marketradar.domain.RadarValidationStats;
import com.smartbox.investory.marketradar.domain.RadarState;
import java.time.LocalDate;
import java.util.List;

public interface RadarOutcomeStore {
  List<RadarSnapshot> unevaluated(int horizonDays, LocalDate cutoff);

  void save(RadarOutcome outcome);

  List<RadarOutcome> outcomes(String symbol, int limit);

  List<RadarValidationStats> validationStats();

  List<RadarValidationObservation> validationObservations(RadarState state, int horizonDays, int limit);
}
