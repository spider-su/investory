package com.smartbox.investory.marketradar.application;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Regular US equity sessions; unscheduled closures require a missing-data state, never a later
 * substitution.
 */
final class OutlookTradingDates {
  private OutlookTradingDates() {}

  static LocalDate previousOrSame(LocalDate d) {
    while (!isTradingDay(d)) d = d.minusDays(1);
    return d;
  }

  static LocalDate nextOrSame(LocalDate d) {
    while (!isTradingDay(d)) d = d.plusDays(1);
    return d;
  }

  static boolean isTradingDay(LocalDate d) {
    if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)
      return false;
    int y = d.getYear();
    Set<LocalDate> holidays =
        Set.of(
            newYear(y),
            nth(y, 1, DayOfWeek.MONDAY, 3),
            nth(y, 2, DayOfWeek.MONDAY, 3),
            easter(y).minusDays(2),
            LocalDate.of(y, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            observed(LocalDate.of(y, 7, 4)),
            nth(y, 9, DayOfWeek.MONDAY, 1),
            nth(y, 11, DayOfWeek.THURSDAY, 4),
            observed(LocalDate.of(y, 12, 25)));
    return !holidays.contains(d) && !(y >= 2022 && d.equals(observed(LocalDate.of(y, 6, 19))));
  }

  private static LocalDate newYear(int y) {
    LocalDate d = LocalDate.of(y, 1, 1);
    return d.getDayOfWeek() == DayOfWeek.SUNDAY ? d.plusDays(1) : d;
  }

  private static LocalDate observed(LocalDate d) {
    return d.getDayOfWeek() == DayOfWeek.SATURDAY
        ? d.minusDays(1)
        : d.getDayOfWeek() == DayOfWeek.SUNDAY ? d.plusDays(1) : d;
  }

  private static LocalDate nth(int y, int month, DayOfWeek day, int n) {
    return LocalDate.of(y, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day));
  }

  private static LocalDate easter(int y) {
    int a = y % 19,
        b = y / 100,
        c = y % 100,
        d = b / 4,
        e = b % 4,
        f = (b + 8) / 25,
        g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30,
        i = c / 4,
        k = c % 4,
        l = (32 + 2 * e + 2 * i - h - k) % 7,
        m = (a + 11 * h + 22 * l) / 451;
    int x = h + l - 7 * m + 114;
    return LocalDate.of(y, x / 31, x % 31 + 1);
  }
}
