package com.smartbox.investory.marketradar.port;

import com.smartbox.investory.marketradar.domain.StrategistForecast;
import java.time.LocalDate;
import java.util.List;

public interface StrategistForecastStore {
  /**
   * @return true when inserted; false when the same source observation already exists.
   */
  boolean insert(StrategistForecast forecast);

  /** Adapter implementations should commit a validated batch atomically. */
  default int insertAll(List<StrategistForecast> forecasts) {
    return (int) forecasts.stream().filter(this::insert).count();
  }

  List<ForecastObservation> observations(LocalDate targetDate);

  record ForecastObservation(long id, StrategistForecast forecast, String quality, String status) {}
}
