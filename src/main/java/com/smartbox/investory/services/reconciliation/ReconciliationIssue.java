package com.smartbox.investory.services.reconciliation;

import java.math.BigDecimal;

public record ReconciliationIssue(
    ReconciliationStatus status,
    ReconciliationCheckpoint checkpoint,
    String location,
    String checkCode,
    String checkName,
    BigDecimal expected,
    BigDecimal actual,
    BigDecimal difference,
    String cause,
    String details,
    String suggestedAction) {}
