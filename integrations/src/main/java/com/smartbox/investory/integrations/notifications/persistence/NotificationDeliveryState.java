package com.smartbox.investory.integrations.notifications.persistence;

public enum NotificationDeliveryState {
  PENDING,
  RETRYABLE,
  DELIVERED,
  EXHAUSTED
}
