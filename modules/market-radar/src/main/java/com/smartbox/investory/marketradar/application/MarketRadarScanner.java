package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarketRadarScanner {
  private final MarketRadarService radar;
  private final RadarSnapshotStore store;

  public MarketRadarScanner(MarketRadarService radar, RadarSnapshotStore store) {
    this.radar = radar;
    this.store = store;
  }

  public List<RadarSnapshot> refresh(List<String> symbols) {
    List<RadarSnapshot> refreshed = new ArrayList<>();
    for (String symbol : symbols) {
      try {
        radar
            .analyze(symbol)
            .ifPresent(
                snapshot -> {
                  store.save(snapshot);
                  refreshed.add(snapshot);
                });
      } catch (RuntimeException e) {
        log.warn("Market Radar scan skipped for {}: {}", symbol, e.getMessage());
      }
    }
    return List.copyOf(refreshed);
  }

  public List<RadarSnapshot> latest() {
    return store.latest();
  }
}
