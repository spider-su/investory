package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class PersistentNotificationEventPublisherTest {
  @Test
  void reportsInsertAndDuplicateOutcomes() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ObjectMapper json = new ObjectMapper();
    var publisher = new PersistentNotificationEventPublisher(jdbc, json);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);

    assertThat(publisher.publish(candidate())).isTrue();
    assertThat(publisher.publish(candidate())).isFalse();
  }

  @Test
  void serializationFailureIsTranslatedToStableBoundaryError() throws Exception {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ObjectMapper json = mock(ObjectMapper.class);
    when(json.writeValueAsString(any())).thenThrow(mock(JacksonException.class));

    assertThatThrownBy(
            () -> new PersistentNotificationEventPublisher(jdbc, json).publish(candidate()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be serialized");
  }

  private static NotificationCandidate candidate() {
    return new NotificationCandidate(
        NotificationEventType.SYSTEM_AUDIT_ERROR,
        NotificationSeverity.ERROR,
        1L,
        "AUDIT",
        "7",
        "audit:7",
        "Audit failed",
        Map.of("code", "C1"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }
}
