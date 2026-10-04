package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.ThemeSnapshot;
import java.util.List;

public interface ThemeSnapshotStore {
  void save(ThemeSnapshot snapshot);

  List<ThemeSnapshot> latest();

  List<ThemeSnapshot> history(String theme, int limit);
}
