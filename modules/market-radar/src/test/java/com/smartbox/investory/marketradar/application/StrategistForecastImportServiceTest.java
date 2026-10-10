package com.smartbox.investory.marketradar.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.marketradar.application.StrategistForecastImportService.ImportRecord;
import com.smartbox.investory.marketradar.domain.StrategistForecast;
import com.smartbox.investory.marketradar.port.StrategistForecastStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategistForecastImportServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

  @Test
  void validatesWholeBatchThenPersistsAndCountsIdempotentDuplicates() {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var forecast = record("https://example.com/forecast", LocalDate.of(2026, 12, 31));

    assertEquals(
        new StrategistForecastImportService.ImportResult(1, 0),
        service.importForecasts(List.of(forecast)));
    assertEquals(
        new StrategistForecastImportService.ImportResult(0, 1),
        service.importForecasts(List.of(forecast)));
    assertEquals(1, store.forecasts.size());
    assertEquals(NOW, store.forecasts.getFirst().observedAt());
  }

  @Test
  void rejectsMalformedOrAmbiguousRecordsBeforeAnyWrite() {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var valid = record("https://example.com/forecast", LocalDate.of(2026, 12, 31));
    var bad = record("file:///tmp/article", LocalDate.of(2026, 12, 31));
    assertThrows(
        IllegalArgumentException.class, () -> service.importForecasts(List.of(valid, bad)));
    assertEquals(0, store.forecasts.size());
  }

  @Test
  void acceptsDistinctPublicationForSameHorizonAsSeparateRevisionInput() {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var first = record("https://example.com/first", LocalDate.of(2026, 12, 31));
    var revision = record("https://example.com/revision", LocalDate.of(2026, 12, 31));
    assertEquals(2, service.importForecasts(List.of(first, revision)).inserted());
  }

  @Test
  void separatesTargetHorizonsAndRejectsRepeatedSourceRowInBatch() {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var yearEnd = record("https://example.com/forecast", LocalDate.of(2026, 12, 31));
    var nextYear = record("https://example.com/forecast", LocalDate.of(2027, 12, 31));
    assertEquals(2, service.importForecasts(List.of(yearEnd, nextYear)).inserted());
    assertThrows(
        IllegalArgumentException.class, () -> service.importForecasts(List.of(yearEnd, yearEnd)));
  }

  private static ImportRecord record(String url, LocalDate target) {
    return new ImportRecord(
        "goldman-sachs",
        "Goldman Sachs Research",
        "Named strategist",
        Instant.parse("2026-05-28T08:00:00Z"),
        target,
        new BigDecimal("7600.00"),
        url,
        "Published forecast title",
        StrategistForecast.SourceType.PRIMARY);
  }

  private static final class RecordingStore implements StrategistForecastStore {
    private final List<StrategistForecast> forecasts = new ArrayList<>();

    @Override
    public boolean insert(StrategistForecast forecast) {
      boolean duplicate =
          forecasts.stream()
              .anyMatch(
                  existing ->
                      existing.sourceUrl().equals(forecast.sourceUrl())
                          && existing.institutionKey().equals(forecast.institutionKey())
                          && existing.targetDate().equals(forecast.targetDate()));
      if (!duplicate) forecasts.add(forecast);
      return !duplicate;
    }

    @Override
    public List<StrategistForecastStore.ForecastObservation> observations(LocalDate targetDate) {
      return List.of();
    }
  }
}
