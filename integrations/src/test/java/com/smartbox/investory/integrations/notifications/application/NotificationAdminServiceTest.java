package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NotificationAdminServiceTest {
  @Test
  void replayDelegatesOnlyExhaustedEventReset() {
    var repository = mock(NotificationEventRepository.class);
    when(repository.replayExhausted(7L, Instant.parse("2026-08-25T10:00:00Z"))).thenReturn(1);
    var service =
        new NotificationAdminService(
            repository, Clock.fixed(Instant.parse("2026-08-25T10:00:00Z"), ZoneOffset.UTC));

    assertThat(service.replay(7L)).isTrue();
    verify(repository).replayExhausted(7L, Instant.parse("2026-08-25T10:00:00Z"));
  }
}
