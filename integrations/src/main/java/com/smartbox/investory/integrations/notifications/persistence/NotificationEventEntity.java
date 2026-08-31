package com.smartbox.investory.integrations.notifications.persistence;

import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_event", schema = "investory")
@Getter
@Setter
public class NotificationEventEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 64)
  private NotificationEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationSeverity severity;

  @Column(name = "portfolio_id")
  private Long portfolioId;

  @Column(name = "source_entity_type", nullable = false, length = 64)
  private String sourceEntityType;

  @Column(name = "source_entity_id", nullable = false, length = 128)
  private String sourceEntityId;

  @Column(nullable = false, unique = true, length = 255)
  private String fingerprint;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private Map<String, String> payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "delivery_state", nullable = false, length = 24)
  private NotificationDeliveryState deliveryState;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;
}
