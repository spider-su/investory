package com.smartbox.investory.integrations.notifications;

import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;

public interface NotificationMessageFormatter {
  NotificationEventType type();

  String format(NotificationEventEntity event);
}
