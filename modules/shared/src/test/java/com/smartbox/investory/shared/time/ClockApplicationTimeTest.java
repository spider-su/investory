package com.smartbox.investory.shared.time;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockApplicationTimeTest {

  @Test
  void derivesBusinessDateFromConfiguredZone() {
    var instant = Instant.parse("2026-09-04T22:30:00Z");
    var time =
        new ClockApplicationTime(Clock.fixed(instant, ZoneOffset.UTC), ZoneId.of("Europe/Warsaw"));

    assertEquals(instant, time.now());
    assertEquals("2026-09-05", time.today().toString());
    assertEquals("2026-09-04T22:30Z", time.now(ZoneOffset.UTC).toString());
  }
}
