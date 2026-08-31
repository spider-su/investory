package com.smartbox.investory.retirement.api.model;

import java.util.List;

public record HistoricalReconciliationView(
    String summary, List<HistoricalReconciliationMetricView> metrics) {
  public HistoricalReconciliationView {
    metrics = List.copyOf(metrics == null ? List.of() : metrics);
  }
}
