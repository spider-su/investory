package com.smartbox.investory.retirement.planning;

public record HistoricalReconciliationMetricView(
    String label,
    String planningValue,
    String referenceValue,
    String difference,
    String status,
    String quality,
    String source) {}
