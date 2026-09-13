package com.smartbox.investory.accounting;

/** Small explicit lifecycle guard; derived readiness is intentionally not persisted here. */
public final class AccountingPeriodLifecycle {
  public PeriodLifecycleStatus transition(
      PeriodLifecycleStatus current,
      PeriodLifecycleStatus target,
      boolean blockingIssues,
      boolean hasFilingEvidence,
      boolean amountsReconcile) {
    if (current == PeriodLifecycleStatus.LOCKED)
      throw new IllegalStateException("Locked period requires explicit reopen");
    if (!allowed(current, target))
      throw new IllegalStateException(
          "Illegal accounting period transition: " + current + " -> " + target);
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

  public PeriodLifecycleStatus reopen(PeriodLifecycleStatus current, String reason) {
    if (current != PeriodLifecycleStatus.LOCKED)
      throw new IllegalStateException("Only a locked period can be reopened");
    if (reason == null || reason.isBlank())
      throw new IllegalArgumentException("A reopen reason is required");
    return PeriodLifecycleStatus.OPEN;
  }

  private boolean allowed(PeriodLifecycleStatus current, PeriodLifecycleStatus target) {
    return switch (current) {
      case OPEN ->
          target == PeriodLifecycleStatus.SOURCES_INCOMPLETE
              || target == PeriodLifecycleStatus.READY_FOR_REVIEW
              || target == PeriodLifecycleStatus.ISSUES;
      case SOURCES_INCOMPLETE ->
          target == PeriodLifecycleStatus.READY_FOR_REVIEW
              || target == PeriodLifecycleStatus.ISSUES;
      case ISSUES -> target == PeriodLifecycleStatus.READY_FOR_REVIEW;
      case READY_FOR_REVIEW -> target == PeriodLifecycleStatus.CONFIRMED;
      case CONFIRMED -> target == PeriodLifecycleStatus.FILED;
      case FILED -> target == PeriodLifecycleStatus.PAID;
      case PAID -> target == PeriodLifecycleStatus.SETTLED;
      case SETTLED -> target == PeriodLifecycleStatus.LOCKED;
      case LOCKED -> false;
    };
  }
}
