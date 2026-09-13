package com.smartbox.investory.accounting;

import java.util.List;

/** Small explicit lifecycle guard; derived readiness is intentionally not persisted here. */
public final class AccountingPeriodLifecycle {
  public enum NextAction {
    WAITING_FOR_SOURCE,
    REVIEW,
    CONFIRM,
    FILE,
    SETTLE,
    LOCK,
    NONE
  }

  public NextAction nextAction(
      PeriodLifecycleStatus status, boolean acquired, boolean blockingIssues) {
    if (!acquired) return NextAction.WAITING_FOR_SOURCE;
    if (blockingIssues) return NextAction.REVIEW;
    return switch (status) {
      case OPEN, SOURCES_INCOMPLETE, ISSUES, READY_FOR_REVIEW -> NextAction.CONFIRM;
      case CONFIRMED -> NextAction.FILE;
      case FILED, PAID -> NextAction.SETTLE;
      case SETTLED -> NextAction.LOCK;
      case LOCKED -> NextAction.NONE;
    };
  }

  public List<String> allowedActions(
      PeriodLifecycleStatus status, boolean sourceDataAcquired, boolean hasIssues) {
    if (!sourceDataAcquired || hasIssues) return List.of();
    if (status == PeriodLifecycleStatus.LOCKED) return List.of("REOPEN");
    return switch (nextAction(status, true, false)) {
      case CONFIRM -> List.of("CONFIRM");
      case FILE -> List.of("FILE");
      case SETTLE -> List.of("SETTLE");
      case LOCK -> List.of("LOCK");
      default -> List.of();
    };
  }

  public PeriodLifecycleStatus transition(
      PeriodLifecycleStatus current,
      PeriodLifecycleStatus target,
      boolean blockingIssues,
      boolean hasFilingEvidence,
      boolean amountsReconcile) {
    if (current == PeriodLifecycleStatus.LOCKED)
      throw new AccountingInvalidTransitionException("Locked period requires explicit reopen");
    if (!allowed(current, target))
      throw new AccountingInvalidTransitionException(
          "Illegal accounting period transition: " + current + " -> " + target);
    if (target == PeriodLifecycleStatus.CONFIRMED && blockingIssues)
      throw new AccountingInvalidTransitionException(
          "Cannot confirm a period with blocking issues");
    if (target == PeriodLifecycleStatus.FILED && !hasFilingEvidence)
      throw new AccountingInvalidTransitionException("Filing evidence is required");
    if (target == PeriodLifecycleStatus.SETTLED && (!hasFilingEvidence || !amountsReconcile))
      throw new AccountingInvalidTransitionException(
          "Filing evidence and reconciled amounts are required");
    if (target == PeriodLifecycleStatus.LOCKED && blockingIssues)
      throw new AccountingInvalidTransitionException("Cannot lock a period with blocking issues");
    return target;
  }

  public PeriodLifecycleStatus reopen(PeriodLifecycleStatus current, String reason) {
    if (current != PeriodLifecycleStatus.LOCKED)
      throw new AccountingInvalidTransitionException("Only a locked period can be reopened");
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
