package com.smartbox.investory.retirement.api.model;

public record HistoricalReconciliationMetricView(
    String label,
    String planningValue,
    String referenceValue,
    String difference,
    String status,
    String quality,
    String source) {}
