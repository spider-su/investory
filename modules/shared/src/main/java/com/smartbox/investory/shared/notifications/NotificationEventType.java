package com.smartbox.investory.shared.notifications;

/** Stable notification codes shared by producers and delivery adapters. */
public enum NotificationEventType {
  DAILY_DIGEST,
  THRESHOLD_ALERT,
  SYSTEM_AUDIT_ERROR,
  IMPORT_FAILED_OR_PARTIAL,
  IMPORT_REFRESH_FINISHED,
  INTEGRATION_JOB_ALERT,
  PLAN_BECAME_UNSUSTAINABLE
}
