package com.smartbox.investory.integrations.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.integrations.notifications.infrastructure.NotificationEventEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationFormatterTest {
  private final ObjectMapper json = new ObjectMapper();
  private final NotificationLinkBuilder links =
      new NotificationLinkBuilder("https://investory.test/");

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
    assertTrue(message.contains("https://investory.test/dashboard/reconciliation?importId=7"));
    assertFalse(message.contains("token"));
    assertFalse(message.contains("stack"));
  }
}
