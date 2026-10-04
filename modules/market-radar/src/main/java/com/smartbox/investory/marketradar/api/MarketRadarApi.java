package com.smartbox.investory.marketradar.api;

import com.smartbox.investory.marketradar.domain.RadarOutcome;
import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.domain.RadarState;
import com.smartbox.investory.marketradar.domain.RadarValidationObservation;
import com.smartbox.investory.marketradar.domain.RadarValidationStats;
import java.util.List;
import java.util.Optional;

public interface MarketRadarApi {

  Optional<RadarSnapshot> analyze(String symbol);

  List<RadarSnapshot> latestSignals();

  List<RadarSnapshot> signalHistory(String symbol, int limit);

  List<RadarOutcome> outcomes(String symbol, int limit);

  List<RadarValidationStats> validationStats();

  List<RadarRunSummary> recentRuns(int limit);

  List<RadarValidationObservation> validationObservations(
      RadarState state, int horizonDays, int limit);
}
