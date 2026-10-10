package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.StrategistForecast;
import java.time.LocalDate;
import java.util.List;

public interface StrategistForecastStore {
  /**
   * @return true when inserted; false when the same source observation already exists.
   */
  boolean insert(StrategistForecast forecast);

  List<ForecastObservation> observations(LocalDate targetDate);

  record ForecastObservation(long id, StrategistForecast forecast, String quality, String status) {}
}
