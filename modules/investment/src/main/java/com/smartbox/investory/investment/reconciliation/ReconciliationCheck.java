package com.smartbox.investory.investment.reconciliation;

import com.smartbox.investory.investment.api.reporting.model.ReconciliationCheckpoint;

public interface ReconciliationCheck {
  ReconciliationCheckpoint checkpoint();

  ReconciliationCheckResult execute(ReconciliationContext context);
}
