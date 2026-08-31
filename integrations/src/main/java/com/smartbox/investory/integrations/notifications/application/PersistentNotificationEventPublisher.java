package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PersistentNotificationEventPublisher implements NotificationEventPublisher {
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public boolean publish(NotificationCandidate candidate) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(candidate.payload());
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Notification payload cannot be serialized", exception);
    }
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO investory.notification_event(
                event_type, severity, portfolio_id, source_entity_type, source_entity_id,
                fingerprint, title, payload, created_at, next_attempt_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (fingerprint) DO NOTHING
            """,
            candidate.type().name(),
            candidate.severity().name(),
            candidate.portfolioId(),
            candidate.sourceEntityType(),
            candidate.sourceEntityId(),
            candidate.fingerprint(),
            candidate.title(),
            payload,
            Timestamp.from(candidate.createdAt()),
            Timestamp.from(candidate.createdAt()));
    return inserted == 1;
  }
}
