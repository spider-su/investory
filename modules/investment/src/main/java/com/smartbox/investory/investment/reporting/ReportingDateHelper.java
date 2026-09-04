package com.smartbox.investory.investment.reporting;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ReportingDateHelper {

  // Reporting dates follow the household's Polish local calendar, not the JVM or database default
  // zone.
  public static final ZoneId REPORTING_ZONE = ZoneId.of("Europe/Warsaw");

  private ReportingDateHelper() {}

  public static LocalDate toReportingDate(ZonedDateTime timestamp) {
    if (timestamp == null) {
      return LocalDate.now(REPORTING_ZONE);
    }
    return timestamp.withZoneSameInstant(REPORTING_ZONE).toLocalDate();
  }

  public static ZonedDateTime now() {
    return ZonedDateTime.now(REPORTING_ZONE);
  }

  public static LocalDate today() {
    return LocalDate.now(REPORTING_ZONE);
  }
}
