package com.smartbox.investory.application.planning;

import java.util.List;

public record HistoricalReconciliation(List<PlanningMetricReconciliation> metrics) {
  public HistoricalReconciliation {
    metrics = List.copyOf(metrics == null ? List.of() : metrics);
  }

  public long matchedCount() {
    return metrics.stream()
        .filter(metric -> metric.status() == ReconciliationStatus.MATCHED)
        .count();
  }

  public long differentCount() {
    return metrics.stream()
        .filter(metric -> metric.status() == ReconciliationStatus.DIFFERENT)
        .count();
  }
}
