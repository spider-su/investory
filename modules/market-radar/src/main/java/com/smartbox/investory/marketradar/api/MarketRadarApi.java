package com.smartbox.investory.marketradar.api;

import com.smartbox.investory.marketradar.domain.RadarSignal;
import java.util.List;

public interface MarketRadarApi {

  List<RadarSignal> currentSignals();
}
