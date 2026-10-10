package com.smartbox.investory.marketradar.application;

import com.smartbox.investory.marketradar.domain.StrategistForecast;
import com.smartbox.investory.marketradar.port.StrategistForecastStore;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Validates a manually curated JSON/CSV-decoded batch before writing any observations. */
@Service
public class StrategistForecastImportService {
  private final StrategistForecastStore store;
  private final Clock clock;

  public StrategistForecastImportService(StrategistForecastStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public ImportResult importForecasts(List<ImportRecord> records) {
    if (records == null || records.isEmpty())
      throw new IllegalArgumentException("Import must contain forecasts");
    Instant observedAt = clock.instant();
    Set<String> batchKeys = new HashSet<>();
    List<StrategistForecast> validated =
        records.stream().map(record -> validate(record, observedAt, batchKeys)).toList();
    int inserted = 0;
    int duplicates = 0;
    for (StrategistForecast forecast : validated) {
      if (store.insert(forecast)) inserted++;
      else duplicates++;
    }
    return new ImportResult(inserted, duplicates);
  }

  private StrategistForecast validate(
      ImportRecord record, Instant observedAt, Set<String> batchKeys) {
    if (record == null) throw new IllegalArgumentException("Forecast record is required");
    String key = required(record.institutionKey(), "institutionKey").toLowerCase(Locale.ROOT);
    String name = required(record.institutionName(), "institutionName");
    String title = required(record.sourceTitle(), "sourceTitle");
    String url = required(record.sourceUrl(), "sourceUrl");
    maxLength(key, 96, "institutionKey");
    maxLength(name, 256, "institutionName");
    maxLength(title, 512, "sourceTitle");
    maxLength(url, 2048, "sourceUrl");
    maxLength(optional(record.strategist()), 256, "strategist");
    URI uri;
    try {
      uri = URI.create(url);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("sourceUrl must be an absolute HTTP(S) URL", ex);
    }
    if (!uri.isAbsolute()
        || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null
        || url.chars().anyMatch(Character::isWhitespace)) {
      throw new IllegalArgumentException("sourceUrl must be an absolute HTTP(S) URL");
    }
    if (record.publishedAt() == null || record.targetDate() == null)
      throw new IllegalArgumentException("publishedAt and targetDate are required");
    if (record.publishedAt().isAfter(observedAt))
      throw new IllegalArgumentException("publishedAt cannot be in the future");
    if (!record
        .targetDate()
        .isAfter(record.publishedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()))
      throw new IllegalArgumentException("targetDate must be after publication date");
    if (record
        .targetDate()
        .isAfter(observedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate().plusYears(10)))
      throw new IllegalArgumentException("targetDate exceeds supported 10-year horizon");
    if (record.targetLevel() == null
        || record.targetLevel().signum() <= 0
        || record.targetLevel().precision() > 14
        || Math.max(0, record.targetLevel().scale()) > 4)
      throw new IllegalArgumentException("targetLevel must be positive and fit numeric(14,4)");
    if (record.sourceType() == null) throw new IllegalArgumentException("sourceType is required");
    String identity = url + "|" + key + "|" + record.targetDate();
    if (!batchKeys.add(identity))
      throw new IllegalArgumentException("Duplicate source observation in import batch: " + url);
    return new StrategistForecast(
        key,
        name,
        optional(record.strategist()),
        record.publishedAt(),
        record.targetDate(),
        record.targetLevel(),
        url,
        title,
        record.sourceType(),
        observedAt);
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(field + " is required");
    return value.trim();
  }

  private static String optional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static void maxLength(String value, int limit, String field) {
    if (value != null && value.length() > limit)
      throw new IllegalArgumentException(field + " exceeds " + limit + " characters");
  }

  public record ImportRecord(
      String institutionKey,
      String institutionName,
      String strategist,
      Instant publishedAt,
      LocalDate targetDate,
      BigDecimal targetLevel,
      String sourceUrl,
      String sourceTitle,
      StrategistForecast.SourceType sourceType) {}

  public record ImportResult(int inserted, int duplicates) {}
}
