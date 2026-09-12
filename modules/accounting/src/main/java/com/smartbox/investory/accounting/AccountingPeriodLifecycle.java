package com.smartbox.investory.accounting;

/** Small explicit lifecycle guard; derived readiness is intentionally not persisted here. */
public final class AccountingPeriodLifecycle {
  public PeriodLifecycleStatus transition(
      PeriodLifecycleStatus current,
      PeriodLifecycleStatus target,
      boolean blockingIssues,
      boolean hasFilingEvidence,
      boolean amountsReconcile) {
    if (current == PeriodLifecycleStatus.LOCKED && target != PeriodLifecycleStatus.OPEN)
      throw new IllegalStateException("Locked period requires explicit reopen");
    if (target == PeriodLifecycleStatus.CONFIRMED && blockingIssues)
      throw new IllegalStateException("Cannot confirm a period with blocking issues");
    if (target == PeriodLifecycleStatus.FILED && !hasFilingEvidence)
      throw new IllegalStateException("Filing evidence is required");
    if (target == PeriodLifecycleStatus.SETTLED && (!hasFilingEvidence || !amountsReconcile))
      throw new IllegalStateException("Filing evidence and reconciled amounts are required");
    if (target == PeriodLifecycleStatus.LOCKED && blockingIssues)
      throw new IllegalStateException("Cannot lock a period with blocking issues");
    return target;
  }
}
