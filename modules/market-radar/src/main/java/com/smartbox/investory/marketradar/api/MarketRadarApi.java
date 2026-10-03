package com.smartbox.investory.marketradar.api;

import com.smartbox.investory.marketradar.domain.RadarOutcome;\nimport com.smartbox.investory.marketradar.domain.RadarSnapshot;
import java.util.List;
import java.util.Optional;

public interface MarketRadarApi {

  Optional<RadarSnapshot> analyze(String symbol);

  List<RadarSnapshot> latestSignals();

  List<RadarSnapshot> signalHistory(String symbol, int limit);\n\n  List<RadarOutcome> outcomes(String symbol, int limit);
}
