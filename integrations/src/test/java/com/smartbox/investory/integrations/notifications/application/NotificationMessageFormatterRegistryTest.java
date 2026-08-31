package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationMessageFormatterRegistryTest {
  @Test
  void dispatchesByEventType() {
    NotificationMessageFormatter formatter = mock(NotificationMessageFormatter.class);
    when(formatter.type()).thenReturn(NotificationEventType.SYSTEM_AUDIT_ERROR);
    var event = new NotificationEventEntity();
    event.setEventType(NotificationEventType.SYSTEM_AUDIT_ERROR);
    when(formatter.format(event)).thenReturn("formatted");

    assertThat(new NotificationMessageFormatterRegistry(List.of(formatter)).format(event))
        .isEqualTo("formatted");
  }

  @Test
  void rejectsDuplicateAndMissingFormatters() {
    NotificationMessageFormatter first = mock(NotificationMessageFormatter.class);
    NotificationMessageFormatter second = mock(NotificationMessageFormatter.class);
    when(first.type()).thenReturn(NotificationEventType.SYSTEM_AUDIT_ERROR);
    when(second.type()).thenReturn(NotificationEventType.SYSTEM_AUDIT_ERROR);
    assertThatThrownBy(() -> new NotificationMessageFormatterRegistry(List.of(first, second)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");

    var event = new NotificationEventEntity();
    event.setEventType(NotificationEventType.SYSTEM_AUDIT_ERROR);
    assertThatThrownBy(() -> new NotificationMessageFormatterRegistry(List.of()).format(event))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing");
  }
}
