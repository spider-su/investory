package com.smartbox.investory.integrations.notifications.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Notification Formatter")
class NotificationFormatterTest {
  private final ObjectMapper json = new ObjectMapper();
  private final NotificationLinkBuilder links =
      new NotificationLinkBuilder("https://investory.test/");

  @DisplayName("import Formatter Contains Action Context Without Sensitive Raw Content")
  @Test
  void importFormatterContainsActionContextWithoutSensitiveRawContent() {
    NotificationEventEntity event = new NotificationEventEntity();
    event.setTitle("Import failed");
    event.setPayload(
        Map.of(
            "importId", "7",
            "broker", "IBKR",
            "status", "FAILED",
            "source", "TELEGRAM",
            "processedCount", "10",
            "importedCount", "0",
            "skippedCount", "8",
            "errorCount", "2",
            "failure", "Invalid header"));

    String message = new ImportFailedOrPartialFormatter(json, links).format(event);

    assertTrue(message.contains("IBKR · FAILED"));
    assertTrue(message.contains("10/0/8/2"));
    assertTrue(message.contains("https://investory.test/dashboard/reconciliation"));
    assertFalse(message.contains("token"));
    assertFalse(message.contains("stack"));
  }

  @Test
  void unsustainablePlanFormatterIncludesDecisionFactsAndDeepLink() {
    NotificationEventEntity event = new NotificationEventEntity();
    event.setTitle("Plan failed");
    event.setEventType(NotificationEventType.PLAN_BECAME_UNSUSTAINABLE);
    event.setPayload(
        Map.of(
            "portfolioId", "3",
            "planId", "9",
            "revisionNumber", "4",
            "firstFailureYear", "2042",
            "firstFailureAge", "67",
            "totalUnfundedAmount", "12000",
            "minimumLiquidAssets", "500",
            "limitingCondition", "LIQUID_ASSETS"));

    String message = new PlanBecameUnsustainableFormatter(json, links).format(event);

    assertTrue(message.contains("2042 (age 67)"));
    assertTrue(message.contains("12000"));
    assertTrue(message.contains("https://investory.test/analysis?portfolioId=3&planId=9"));
  }

  @Test
  void systemAuditFormatterUsesFallbackChecksAndReconciliationLink() {
    NotificationEventEntity event = new NotificationEventEntity();
    event.setTitle("Audit failed");
    event.setEventType(NotificationEventType.SYSTEM_AUDIT_ERROR);
    event.setPayload(
        Map.of(
            "auditId", "12",
            "triggerSource", "IMPORT",
            "errorCount", "2",
            "warningCount", "1"));

    String message = new SystemAuditErrorFormatter(json, links).format(event);

    assertTrue(message.contains("Errors/warnings: 2/1"));
    assertTrue(message.contains("Checks: Unavailable"));
    assertTrue(message.contains("https://investory.test/dashboard/reconciliation"));
  }
}
