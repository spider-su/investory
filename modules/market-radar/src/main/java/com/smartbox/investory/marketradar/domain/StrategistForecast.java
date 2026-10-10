package com.smartbox.investory.marketradar.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A source-backed, immutable observation of an S&P 500 price-index target. */
public record StrategistForecast(
    String institutionKey,
    String institutionName,
    String strategist,
    Instant publishedAt,
    LocalDate targetDate,
    BigDecimal targetLevel,
    String sourceUrl,
    String sourceTitle,
    SourceType sourceType,
    Instant observedAt) {

  public enum SourceType {
    PRIMARY,
    SECONDARY
  }
}
