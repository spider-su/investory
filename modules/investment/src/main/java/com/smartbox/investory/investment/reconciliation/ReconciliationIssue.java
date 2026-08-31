package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;
import com.smartbox.investory.investment.api.reporting.model.ReconciliationStatus;
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
