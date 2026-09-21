package com.smartbox.investory.ryczalt.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/** Calculates the simple statutory payment deadline used by the native Ryczalt module. */
public final class ObligationDueDateCalculator {
  private ObligationDueDateCalculator() {}

  public static LocalDate calculate(YearMonth period, ObligationType type) {
    int day = type == ObligationType.VAT ? 25 : 20;
    LocalDate candidate = period.plusMonths(1).atDay(day);
    while (isNonWorkingDay(candidate)) {
      candidate = candidate.plusDays(1);
    }
    return candidate;
  }

  private static boolean isNonWorkingDay(LocalDate date) {
    return date.getDayOfWeek() == DayOfWeek.SATURDAY
        || date.getDayOfWeek() == DayOfWeek.SUNDAY
        || isPolishHoliday(date);
  }

  private static boolean isPolishHoliday(LocalDate date) {
    LocalDate easter = easter(date.getYear());
    return date.equals(LocalDate.of(date.getYear(), 1, 1))
        || date.equals(LocalDate.of(date.getYear(), 1, 6))
        || date.equals(easter)
        || date.equals(easter.plusDays(1))
        || date.equals(LocalDate.of(date.getYear(), 5, 1))
        || date.equals(LocalDate.of(date.getYear(), 5, 3))
        || date.equals(easter.plusDays(49))
        || date.equals(easter.plusDays(60))
        || date.equals(LocalDate.of(date.getYear(), 8, 15))
        || date.equals(LocalDate.of(date.getYear(), 11, 1))
        || date.equals(LocalDate.of(date.getYear(), 11, 11))
        || date.equals(LocalDate.of(date.getYear(), 12, 24))
        || date.equals(LocalDate.of(date.getYear(), 12, 25))
        || date.equals(LocalDate.of(date.getYear(), 12, 26));
  }

  private static LocalDate easter(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31;
    int day = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(year, month, day);
  }
}
