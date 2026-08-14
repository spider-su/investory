package com.smartbox.investory.services.reconciliation;

public interface ReconciliationCheck {
  ReconciliationCheckpoint checkpoint();

  ReconciliationCheckResult execute(ReconciliationContext context);
}
