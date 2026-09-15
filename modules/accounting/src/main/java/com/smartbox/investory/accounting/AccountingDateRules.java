package com.smartbox.investory.accounting;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Shared date rules for accounting acquisition and calculation. */
public final class AccountingDateRules {
  private AccountingDateRules() {}

  public static LocalDate accountingPeriod(
      LocalDate saleDate, LocalDate issueDate, LocalDate paymentDate, boolean correction) {
    if (correction && issueDate != null) return issueDate.withDayOfMonth(1);
    LocalDate invoiceDate =
        saleDate == null
            ? issueDate
            : issueDate == null ? saleDate : saleDate.isBefore(issueDate) ? saleDate : issueDate;
    if (invoiceDate == null) {
      throw new IllegalArgumentException("Sale or issue date is required");
    }
    return invoiceDate.withDayOfMonth(1);
  }

  public static LocalDate priorBusinessDay(LocalDate date) {
    if (date == null) return null;
    LocalDate result = date.minusDays(1);
    while (result.getDayOfWeek() == DayOfWeek.SATURDAY
        || result.getDayOfWeek() == DayOfWeek.SUNDAY
        || isPolishHoliday(result)) {
      result = result.minusDays(1);
    }
    return result;
  }

  public static boolean isPolishHoliday(LocalDate date) {
    if ((date.getMonthValue() == 1 && (date.getDayOfMonth() == 1 || date.getDayOfMonth() == 6))
        || (date.getMonthValue() == 5 && (date.getDayOfMonth() == 1 || date.getDayOfMonth() == 3))
        || (date.getMonthValue() == 8 && date.getDayOfMonth() == 15)
        || (date.getMonthValue() == 11 && (date.getDayOfMonth() == 1 || date.getDayOfMonth() == 11))
        || (date.getMonthValue() == 12
            && (date.getDayOfMonth() == 25 || date.getDayOfMonth() == 26))) return true;
    int year = date.getYear();
    int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
    int f = (b + 8) / 25, g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
    LocalDate easter =
        LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1);
    return date.equals(easter.plusDays(1)) || date.equals(easter.plusDays(60));
  }
}
