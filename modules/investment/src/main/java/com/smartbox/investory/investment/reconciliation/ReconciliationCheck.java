package com.smartbox.investory.investment.reconciliation;

public interface ReconciliationCheck {
  ReconciliationCheckpoint checkpoint();

  ReconciliationCheckResult execute(ReconciliationContext context);
}
