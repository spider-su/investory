package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.RadarSignal;
import java.util.List;

public interface MarketSignalSource {

  List<RadarSignal> loadSignals();
}
