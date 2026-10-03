package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MarketRadarScanner {
  private static final System.Logger LOGGER =
      System.getLogger(MarketRadarScanner.class.getName());

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
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Market Radar scan skipped for " + symbol + ": " + e.getMessage());
      }
    }
    return List.copyOf(refreshed);
  }

  public List<RadarSnapshot> latest() {
    return store.latest();
  }
}
