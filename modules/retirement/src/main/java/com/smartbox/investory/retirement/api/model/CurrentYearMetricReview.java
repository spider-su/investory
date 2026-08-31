package com.smartbox.investory.retirement.api.model;

/** Presentation value showing the current-year amount, baseline, and source. */
public record CurrentYearMetricReview(
    PlanningMetric metric,
    String label,
    String currentValue,
    String baselineValue,
    String source,
    boolean manual,
    String note) {}
