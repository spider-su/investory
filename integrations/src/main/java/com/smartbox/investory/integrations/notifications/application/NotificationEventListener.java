package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.shared.notifications.ImportFinalizedEvent;
import com.smartbox.investory.shared.notifications.PlanRevisionReviewedEvent;
import com.smartbox.investory.shared.notifications.SystemAuditCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Wakes delivery after commit; the database outbox remains the delivery source of truth. */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
  private final NotificationEventDispatcher dispatcher;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onImportFinalized(ImportFinalizedEvent event) {
    dispatcher.dispatchPending();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSystemAuditCompleted(SystemAuditCompletedEvent event) {
    dispatcher.dispatchPending();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlanRevisionReviewed(PlanRevisionReviewedEvent event) {
    dispatcher.dispatchPending();
  }
}
