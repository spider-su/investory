package com.smartbox.investory.integrations.notifications.application;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageFormatterRegistry {
  private final Map<NotificationEventType, NotificationMessageFormatter> formatters;

  public NotificationMessageFormatterRegistry(List<NotificationMessageFormatter> formatters) {
    Map<NotificationEventType, NotificationMessageFormatter> registered =
        new EnumMap<>(NotificationEventType.class);
    for (NotificationMessageFormatter formatter : formatters) {
      if (registered.put(formatter.type(), formatter) != null)
        throw new IllegalStateException("Duplicate notification formatter: " + formatter.type());
    }
    this.formatters = Map.copyOf(registered);
  }

  public String format(NotificationEventEntity event) {
    NotificationMessageFormatter formatter = formatters.get(event.getEventType());
    if (formatter == null)
      throw new IllegalStateException("Missing notification formatter: " + event.getEventType());
    return formatter.format(event);
  }
}
