package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import java.util.List;

public record RadarScanResult(
    int attempted, int stored, int noData, int failed, List<RadarSnapshot> snapshots) {

  public RadarScanResult {
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
  }
}
