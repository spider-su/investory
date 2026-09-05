package com.smartbox.investory.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

public final class ClockApplicationTime implements ApplicationTime {

  private final Clock clock;
  private final ZoneId businessZone;

  public ClockApplicationTime(Clock clock, ZoneId businessZone) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
  }

  @Override
  public Instant now() {
    return clock.instant();
  }

  @Override
  public LocalDate today() {
    return LocalDate.ofInstant(now(), businessZone);
  }

  @Override
  public ZonedDateTime now(ZoneId zoneId) {
    return ZonedDateTime.ofInstant(now(), Objects.requireNonNull(zoneId, "zoneId"));
  }

  @Override
  public ZoneId businessZone() {
    return businessZone;
  }

  @Override
  public Clock clock() {
    return clock;
  }
}
