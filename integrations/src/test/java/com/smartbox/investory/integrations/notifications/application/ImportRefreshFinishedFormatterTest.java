package com.smartbox.investory.integrations.notifications.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ImportRefreshFinishedFormatterTest {
  @Test
  void successAndFailureAreDistinctAndEscaped() {
    var links = mock(NotificationLinkBuilder.class);
    when(links.link(anyString())).thenAnswer(i -> "https://example.test" + i.getArgument(0));
    var formatter = new ImportRefreshFinishedFormatter(new ObjectMapper(), links);
    var event = new NotificationEventEntity();
    event.setPortfolioId(7L);
    event.setTitle("Refresh <done>");
    event.setPayload(Map.of("importId", "42", "broker", "IBKR", "status", "COMPLETED"));
    String success = formatter.format(event);
    assertTrue(success.contains("✅"));
    assertTrue(success.contains("&lt;done&gt;"));
    assertTrue(success.contains("/portfolios/7/dashboard"));
    event.setPayload(Map.of("importId", "42", "broker", "IBKR", "status", "FAILED"));
    String failure = formatter.format(event);
    assertTrue(failure.contains("❌"));
    assertTrue(failure.contains("operations were saved"));
    assertTrue(failure.contains("do not re-upload"));
  }
}
