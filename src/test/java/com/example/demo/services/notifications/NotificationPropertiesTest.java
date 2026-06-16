package com.example.demo.services.notifications;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationPropertiesTest {

    @Test
    void settersAndGettersRoundTrip() {
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.setDrawdownThresholdPct(12.5);
        properties.setBigMoveThresholdPct(7.0);
        properties.setConcentrationThresholdPct(33.0);
        properties.setStaleImportDays(14);

        assertEquals(true, properties.isEnabled());
        assertEquals(12.5, properties.getDrawdownThresholdPct());
        assertEquals(7.0, properties.getBigMoveThresholdPct());
        assertEquals(33.0, properties.getConcentrationThresholdPct());
        assertEquals(14, properties.getStaleImportDays());
    }
}

