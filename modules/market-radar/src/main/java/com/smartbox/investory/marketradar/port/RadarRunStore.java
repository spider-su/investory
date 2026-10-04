package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.RadarRunSummary;
import java.util.List;

public interface RadarRunStore {
  void save(RadarRunSummary run);

  List<RadarRunSummary> recent(int limit);
}
