package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.api.MarketRadarApi;
import com.smartbox.investory.marketradar.domain.RadarSignal;
import com.smartbox.investory.marketradar.port.MarketSignalSource;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MarketRadarService implements MarketRadarApi {

  private final List<MarketSignalSource> signalSources;

  public MarketRadarService(List<MarketSignalSource> signalSources) {
    this.signalSources = List.copyOf(signalSources);
  }

  @Override
  public List<RadarSignal> currentSignals() {
    return signalSources.stream()
        .flatMap(source -> source.loadSignals().stream())
        .sorted(Comparator.comparing(RadarSignal::observedAt).reversed())
        .toList();
  }
}
