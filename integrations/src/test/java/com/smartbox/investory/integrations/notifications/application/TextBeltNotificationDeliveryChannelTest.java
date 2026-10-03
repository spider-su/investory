package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.notifications.persistence.NotificationEventEntity;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TextBeltNotificationDeliveryChannelTest {
  @Test
  void sendsOnlyConfiguredLifecycleThresholdAlerts() {
    var channel =
        new TextBeltNotificationDeliveryChannel(
            RestClient.builder(),
            true,
            "+48123456789",
            "textbelt",
            "RENTAL_CONTRACT_EXPIRING,BOND_MATURITY_APPROACHING");

    var rent = threshold("RENTAL_CONTRACT_EXPIRING");
    var drawdown = threshold("DRAWDOWN");

    assertThat(channel.supports(rent)).isTrue();
    assertThat(channel.supports(drawdown)).isFalse();
    assertThat(channel.bestEffort()).isTrue();
  }

  @Test
  void disabledSmsSupportsNothing() {
    var channel =
        new TextBeltNotificationDeliveryChannel(
            RestClient.builder(), false, "+48123456789", "textbelt", "RENTAL_CONTRACT_EXPIRING");
    assertThat(channel.supports(threshold("RENTAL_CONTRACT_EXPIRING"))).isFalse();
  }

  private static NotificationEventEntity threshold(String rule) {
    var event = new NotificationEventEntity();
    event.setEventType(NotificationEventType.THRESHOLD_ALERT);
    event.setPayload(Map.of("rule", rule));
    return event;
  }
}
