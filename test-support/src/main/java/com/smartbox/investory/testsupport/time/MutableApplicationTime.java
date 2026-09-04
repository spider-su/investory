package com.smartbox.investory.testsupport.time;

import com.smartbox.investory.shared.time.ApplicationTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Thread-safe controllable application time for tests. */
public final class MutableApplicationTime implements ApplicationTime {

  private final AtomicReference<Instant> instant;
  private final ZoneId businessZone;

  public MutableApplicationTime(Instant initialInstant, ZoneId businessZone) {
    this.instant = new AtomicReference<>(Objects.requireNonNull(initialInstant, "initialInstant"));
    this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
  }

  public static MutableApplicationTime fixed(Instant instant, ZoneId businessZone) {
    return new MutableApplicationTime(instant, businessZone);
  }

  public void set(Instant newInstant) {
    instant.set(Objects.requireNonNull(newInstant, "newInstant"));
  }

  public void advance(Duration duration) {
    instant.updateAndGet(current -> current.plus(Objects.requireNonNull(duration, "duration")));
  }

  @Override
  public Instant now() {
    return instant.get();
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
    return new MutableClock(this, businessZone);
  }

  private static final class MutableClock extends Clock {

    private final MutableApplicationTime time;
    private final ZoneId zone;

    private MutableClock(MutableApplicationTime time, ZoneId zone) {
      this.time = time;
      this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
      return new MutableClock(time, Objects.requireNonNull(newZone, "newZone"));
    }

    @Override
    public Instant instant() {
      return time.now();
    }
  }
}
