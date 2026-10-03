package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.RadarSnapshot;
import java.util.List;

public interface RadarSnapshotStore {
  void save(RadarSnapshot snapshot);

  List<RadarSnapshot> latest();
}
