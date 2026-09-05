package com.smartbox.investory.integrations;

import com.smartbox.investory.shared.time.ApplicationTime;
import com.smartbox.investory.shared.time.ClockApplicationTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class FixedTestTime {

  public static final ApplicationTime TIME =
      new ClockApplicationTime(
          Clock.fixed(Instant.parse("2026-09-05T08:00:00Z"), ZoneOffset.UTC),
          ZoneId.of("Europe/Warsaw"));

  private FixedTestTime() {}
}
