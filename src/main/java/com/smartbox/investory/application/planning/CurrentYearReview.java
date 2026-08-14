package com.smartbox.investory.application.planning;

import java.util.List;

/** Compact current-year provenance/readiness model for Plan vs Reality. */
public record CurrentYearReview(
    String status,
    List<String> missingMetrics,
    List<CurrentYearMetricReview> metrics,
    boolean baselineSet) {
  public CurrentYearReview {
    missingMetrics = List.copyOf(missingMetrics == null ? List.of() : missingMetrics);
    metrics = List.copyOf(metrics == null ? List.of() : metrics);
  }
}
