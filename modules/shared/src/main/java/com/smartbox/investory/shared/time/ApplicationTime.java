package com.smartbox.investory.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Application wall-clock boundary. */
public interface ApplicationTime {

  Instant now();

  LocalDate today();

  ZonedDateTime now(ZoneId zoneId);

  ZoneId businessZone();

  Clock clock();
}
