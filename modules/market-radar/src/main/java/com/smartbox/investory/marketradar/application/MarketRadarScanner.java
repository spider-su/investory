package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import com.smartbox.investory.marketradar.port.RadarSnapshotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MarketRadarScanner {
  private static final System.Logger LOGGER = System.getLogger(MarketRadarScanner.class.getName());

  private final MarketRadarService radar;
  private final RadarSnapshotStore store;

  public MarketRadarScanner(MarketRadarService radar, RadarSnapshotStore store) {
    this.radar = radar;
    this.store = store;
  }

  public RadarScanResult refresh(List<String> symbols) {
    List<RadarSnapshot> refreshed = new ArrayList<>();
    int noData = 0;
    int failed = 0;

    for (String symbol : symbols) {
      try {
        Optional<RadarSnapshot> snapshot = radar.analyze(symbol);
        if (snapshot.isEmpty()) {
          noData++;
          continue;
        }
        RadarSnapshot value = snapshot.orElseThrow();
        store.save(value);
        refreshed.add(value);
      } catch (RuntimeException e) {
        failed++;
        LOGGER.log(
            System.Logger.Level.WARNING,
            "Market Radar scan skipped for " + symbol + ": " + e.getMessage());
      }
    }

    return new RadarScanResult(symbols.size(), refreshed.size(), noData, failed, refreshed);
  }

  public List<RadarSnapshot> latest() {
    return store.latest();
  }
}
