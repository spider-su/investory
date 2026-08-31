package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;

public interface NotificationMessageFormatter {
  NotificationEventType type();

  String format(NotificationEventEntity event);
}
