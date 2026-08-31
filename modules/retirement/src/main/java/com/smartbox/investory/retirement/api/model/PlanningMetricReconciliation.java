package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

public record PlanningMetricReconciliation(
    PlanningMetric metric,
    BigDecimal planningValue,
    BigDecimal referenceValue,
    BigDecimal difference,
    ReconciliationStatus status,
    ReconciliationQuality quality,
    String source,
    BigDecimal tolerance) {}
