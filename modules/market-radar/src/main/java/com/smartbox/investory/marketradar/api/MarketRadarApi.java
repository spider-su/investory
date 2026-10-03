package com.smartbox.investory.marketradar.api;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import java.util.Optional;

public interface MarketRadarApi {

  Optional<RadarSnapshot> analyze(String symbol);
}
