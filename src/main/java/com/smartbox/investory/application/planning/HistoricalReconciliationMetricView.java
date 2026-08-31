package com.smartbox.investory.application.planning;

public record HistoricalReconciliationMetricView(
    String label,
    String planningValue,
    String referenceValue,
    String difference,
    String status,
    String quality,
    String source) {}
