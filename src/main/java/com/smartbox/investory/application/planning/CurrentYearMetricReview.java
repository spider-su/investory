package com.smartbox.investory.application.planning;

/** Presentation value showing the current-year amount, baseline, and source. */
public record CurrentYearMetricReview(
    PlanningMetric metric,
    String label,
    String currentValue,
    String baselineValue,
    String source,
    boolean manual,
    String note) {}
