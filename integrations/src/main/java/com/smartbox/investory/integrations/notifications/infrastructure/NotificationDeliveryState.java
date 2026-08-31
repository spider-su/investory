package com.smartbox.investory.integrations.notifications.infrastructure;

public enum NotificationDeliveryState {
  PENDING,
  RETRYABLE,
  DELIVERED,
  EXHAUSTED
}
