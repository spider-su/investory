package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.StrategistForecast;

public interface StrategistForecastStore {
  /**
   * @return true when inserted; false when the same source observation already exists.
   */
  boolean insert(StrategistForecast forecast);
}
