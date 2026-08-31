package com.smartbox.investory.retirement.planning;

import java.util.List;

public record HistoricalReconciliationView(
    String summary, List<HistoricalReconciliationMetricView> metrics) {
  public HistoricalReconciliationView {
    metrics = List.copyOf(metrics == null ? List.of() : metrics);
  }
}
