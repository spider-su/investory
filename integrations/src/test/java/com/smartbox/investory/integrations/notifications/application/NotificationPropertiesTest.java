package com.smartbox.investory.integrations.notifications.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notification Properties")
class NotificationPropertiesTest {

  @DisplayName("setters And Getters Round Trip")
  @Test
  void settersAndGettersRoundTrip() {
    NotificationProperties properties = new NotificationProperties();
    properties.setEnabled(true);
    properties.setDrawdownThresholdPct(12.5);
    properties.setDrawdownCooldownHours(48);
    properties.setConcentrationThresholdPct(33.0);
    properties.setStaleImportDays(14);

    assertTrue(properties.isEnabled());
    assertEquals(12.5, properties.getDrawdownThresholdPct());
    assertEquals(48, properties.getDrawdownCooldownHours());
    assertEquals(33.0, properties.getConcentrationThresholdPct());
    assertEquals(14, properties.getStaleImportDays());
  }
}
