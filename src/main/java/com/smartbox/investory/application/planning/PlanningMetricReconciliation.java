package com.smartbox.investory.application.planning;

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
