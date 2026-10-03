package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import java.util.List;
import java.util.Optional;

public interface RadarSnapshotStore {
  void save(RadarSnapshot snapshot);

  List<RadarSnapshot> latest();

  List<RadarSnapshot> history(String symbol, int limit);

  Optional<RadarSnapshot> previous(String symbol, java.time.LocalDate before);
}
