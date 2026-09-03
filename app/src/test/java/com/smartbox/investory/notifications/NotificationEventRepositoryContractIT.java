package com.smartbox.investory.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.notifications.persistence.NotificationDeliveryState;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootTest(
    classes = NotificationEventRepositoryContractIT.NotificationRepositoryTestConfiguration.class)
class NotificationEventRepositoryContractIT extends FastDatabaseTest {
  @Autowired private NotificationEventRepository events;

  private Long eventId;

  @AfterEach
  void cleanUp() {
    if (eventId != null) {
      events.deleteById(eventId);
    }
  }

  @Test
  void productionClaimAllowsOnlyOneConcurrentWorker() throws Exception {
    eventId = insertEvent();
    Instant now = Instant.now();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> claimAfterStart(ready, start, "first", now));
      var second = executor.submit(() -> claimAfterStart(ready, start, "second", now));
      ready.await();
      start.countDown();

      assertThat(first.get() + second.get()).isEqualTo(1);
    }
    assertThat(events.findById(eventId).orElseThrow().getAttemptCount()).isEqualTo(1);
  }

  @Test
  void productionClaimReclaimsExpiredLeaseAndExhaustsRetry() {
    eventId = insertEvent();
    Instant now = Instant.now();

    assertThat(events.claim(eventId, "expired-owner", now, now.minusSeconds(1), 2)).isEqualTo(1);
    assertThat(
            events.markFailed(
                eventId,
                "expired-owner",
                NotificationDeliveryState.RETRYABLE.name(),
                now.minusSeconds(1),
                "temporary failure"))
        .isEqualTo(1);
    assertThat(events.claim(eventId, "retry-owner", now, now.plusSeconds(300), 2)).isEqualTo(1);
    assertThat(
            events.markFailed(
                eventId,
                "retry-owner",
                NotificationDeliveryState.EXHAUSTED.name(),
                now,
                "final failure"))
        .isEqualTo(1);

    assertThat(events.findById(eventId).orElseThrow())
        .satisfies(
            event -> {
              assertThat(event.getDeliveryState()).isEqualTo(NotificationDeliveryState.EXHAUSTED);
              assertThat(event.getAttemptCount()).isEqualTo(2);
            });
    assertThat(events.claim(eventId, "after-exhaustion", now, now.plusSeconds(300), 2)).isZero();
  }

  @Test
  void terminalTransitionRequiresTheClaimingWorkerToken() {
    eventId = insertEvent();
    Instant now = Instant.now();

    assertThat(events.claim(eventId, "owner", now, now.plusSeconds(300), 5)).isEqualTo(1);
    assertThat(events.markDelivered(eventId, "wrong-owner", now)).isZero();
    assertThat(events.markDelivered(eventId, "owner", now)).isEqualTo(1);
    assertThat(events.findById(eventId).orElseThrow().getDeliveryState())
        .isEqualTo(NotificationDeliveryState.DELIVERED);
  }

  private int claimAfterStart(CountDownLatch ready, CountDownLatch start, String token, Instant now)
      throws Exception {
    ready.countDown();
    start.await();
    return events.claim(eventId, token, now, now.plusSeconds(300), 5);
  }

  private Long insertEvent() {
    NotificationEventEntity event = new NotificationEventEntity();
    event.setEventType(NotificationEventType.IMPORT_FAILED_OR_PARTIAL);
    event.setSeverity(NotificationSeverity.ERROR);
    event.setSourceEntityType("TEST");
    event.setSourceEntityId("repository-contract");
    event.setFingerprint("repository-contract:" + UUID.randomUUID());
    event.setTitle("repository contract");
    event.setPayload(Map.of("message", "contract"));
    event.setCreatedAt(Instant.now());
    event.setDeliveryState(NotificationDeliveryState.PENDING);
    event.setAttemptCount(0);
    event.setNextAttemptAt(Instant.now());
    return events.saveAndFlush(event).getId();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = NotificationEventEntity.class)
  @EnableJpaRepositories(basePackageClasses = NotificationEventRepository.class)
  static class NotificationRepositoryTestConfiguration {}
}
