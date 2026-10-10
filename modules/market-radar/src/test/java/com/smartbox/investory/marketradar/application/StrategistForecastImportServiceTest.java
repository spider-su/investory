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

  @Test
  void retainsLaterPublicationOnSameSourceUrl() {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var first = record("https://example.com/forecast", LocalDate.of(2026, 12, 31));
    var revision =
        new ImportRecord(
            first.institutionKey(),
            first.institutionName(),
            first.strategist(),
            first.publishedAt().plusSeconds(86400),
            first.targetDate(),
            new BigDecimal("8000"),
            first.sourceUrl(),
            first.sourceTitle(),
            first.sourceType());
    assertEquals(2, service.importForecasts(List.of(first, revision)).inserted());
  }

  @Test
  void acceptsLargestNumeric14Scale4Target() {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var value =
        withTarget(
            record("https://example.com/boundary", LocalDate.of(2026, 12, 31)), "9999999999.9999");
    assertEquals(1, service.importForecasts(List.of(value)).inserted());
    assertEquals(new BigDecimal("9999999999.9999"), store.forecasts.getFirst().targetLevel());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"10000000000", "1E+10", "1E+2147483647", "0.00001"})
  void rejectsIntegerOverflowAndExcessScaleBeforeWritingAnyPartOfBatch(String level) {
    var store = new RecordingStore();
    var service = new StrategistForecastImportService(store, Clock.fixed(NOW, ZoneOffset.UTC));
    var valid = record("https://example.com/valid", LocalDate.of(2026, 12, 31));
    var invalid =
        withTarget(record("https://example.com/invalid", LocalDate.of(2026, 12, 31)), level);
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> service.importForecasts(List.of(valid, invalid)));
    org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("targetLevel"));
    assertEquals(0, store.forecasts.size());
  }

  private static ImportRecord withTarget(ImportRecord r, String level) {
    return new ImportRecord(
        r.institutionKey(),
        r.institutionName(),
        r.strategist(),
        r.publishedAt(),
        r.targetDate(),
        new BigDecimal(level),
        r.sourceUrl(),
        r.sourceTitle(),
        r.sourceType());
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
                          && existing.targetDate().equals(forecast.targetDate())
                          && existing.publishedAt().equals(forecast.publishedAt()));
      if (!duplicate) forecasts.add(forecast);
      return !duplicate;
    }

    @Override
    public List<StrategistForecastStore.ForecastObservation> observations(LocalDate targetDate) {
      return List.of();
    }
  }
}
