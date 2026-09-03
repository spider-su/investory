package com.smartbox.investory.integrations.notifications.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationEventRepository extends JpaRepository<NotificationEventEntity, Long> {
  @Query(
      value =
          """
          SELECT id FROM investory.notification_event
          WHERE attempt_count < :maxAttempts
            AND ((delivery_state IN ('PENDING', 'RETRYABLE') AND next_attempt_at <= :now)
              OR (delivery_state = 'PROCESSING' AND processing_lease_until <= :now))
          ORDER BY created_at ASC
          LIMIT 50
          """,
      nativeQuery = true)
  List<Long> findDueIds(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query(
      value =
          """
          UPDATE investory.notification_event
          SET delivery_state = 'PROCESSING',
              processing_token = :token,
              processing_lease_until = :leaseUntil,
              attempt_count = attempt_count + 1,
              last_attempt_at = :now,
              last_error = NULL,
              delivered_at = NULL
          WHERE id = :id
            AND attempt_count < :maxAttempts
            AND ((delivery_state IN ('PENDING', 'RETRYABLE') AND next_attempt_at <= :now)
              OR (delivery_state = 'PROCESSING' AND processing_lease_until <= :now))
          """,
      nativeQuery = true)
  int claim(
      @Param("id") long id,
      @Param("token") String token,
      @Param("now") Instant now,
      @Param("leaseUntil") Instant leaseUntil,
      @Param("maxAttempts") int maxAttempts);

  @Modifying
  @Transactional
  @Query(
      value =
          """
          UPDATE investory.notification_event
          SET delivery_state = 'DELIVERED', delivered_at = :now,
              processing_token = NULL, processing_lease_until = NULL,
              last_error = NULL
          WHERE id = :id AND delivery_state = 'PROCESSING' AND processing_token = :token
          """,
      nativeQuery = true)
  int markDelivered(@Param("id") long id, @Param("token") String token, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query(
      value =
          """
          UPDATE investory.notification_event
          SET delivery_state = :state, next_attempt_at = :nextAttemptAt,
              last_error = :error, processing_token = NULL, processing_lease_until = NULL
          WHERE id = :id AND delivery_state = 'PROCESSING' AND processing_token = :token
          """,
      nativeQuery = true)
  int markFailed(
      @Param("id") long id,
      @Param("token") String token,
      @Param("state") String state,
      @Param("nextAttemptAt") Instant nextAttemptAt,
      @Param("error") String error);

  List<NotificationEventEntity> findTop100ByOrderByCreatedAtDesc();

  long countByDeliveryState(NotificationDeliveryState deliveryState);

  Optional<Instant> findFirstCreatedAtByDeliveryStateOrderByCreatedAtAsc(
      NotificationDeliveryState deliveryState);

  @Modifying
  @Transactional
  @Query(
      value =
          """
          UPDATE investory.notification_event
          SET delivery_state = 'PENDING', attempt_count = 0, next_attempt_at = :now,
              last_attempt_at = NULL, delivered_at = NULL, last_error = NULL,
              processing_token = NULL, processing_lease_until = NULL
          WHERE id = :id AND delivery_state = 'EXHAUSTED'
          """,
      nativeQuery = true)
  int replayExhausted(@Param("id") long id, @Param("now") Instant now);
}
