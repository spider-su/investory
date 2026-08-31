package com.smartbox.investory.integrations.notifications;

/** One external delivery adapter. Successful return confirms delivery. */
public interface NotificationDeliveryChannel {
  void send(String message);
}
