package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;

/**
 * One external delivery adapter. Successful return confirms delivery unless the channel is
 * best-effort.
 */
public interface NotificationDeliveryChannel {
  default boolean supports(NotificationEventEntity event) {
    return true;
  }

  default boolean bestEffort() {
    return false;
  }

  void send(String message);
}
