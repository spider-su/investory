package com.smartbox.investory.marketradar.api;

import com.smartbox.investory.marketradar.domain.ThemeSnapshot;
import java.util.List;

public interface ThemeRadarApi {
  List<ThemeSnapshot> latestThemes();

  List<ThemeSnapshot> themeHistory(String theme, int limit);
}
