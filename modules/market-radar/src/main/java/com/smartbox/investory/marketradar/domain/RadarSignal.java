package com.smartbox.investory.marketradar.domain;

import java.time.Instant;

public record RadarSignal(String ticker, RadarSignalType type, String summary, Instant observedAt) {

  public RadarSignal {
    ticker = normalizeTicker(ticker);
    if (type == null) {
      throw new IllegalArgumentException("type is required");
    }
    if (summary == null || summary.isBlank()) {
      throw new IllegalArgumentException("summary is required");
    }
    if (observedAt == null) {
      throw new IllegalArgumentException("observedAt is required");
    }
  }

  private static String normalizeTicker(String ticker) {
    if (ticker == null || ticker.isBlank()) {
      throw new IllegalArgumentException("ticker is required");
    }
    return ticker.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
