package com.smartbox.investory.accounting;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Due dates for the currently supported monthly Polish JDG POC. */
@Component
public class AccountingDueDatePolicy {
  private final Clock clock;

  public AccountingDueDatePolicy() {
    this(Clock.systemDefaultZone());
  }

  public AccountingDueDatePolicy(Clock clock) {
    this.clock = clock;
  }

  public LocalDate dueDate(LocalDate period, String obligationType) {
    int day = "VAT".equals(obligationType) ? 25 : 20;
    LocalDate date = period.plusMonths(1).withDayOfMonth(day);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY
        || date.getDayOfWeek() == DayOfWeek.SUNDAY
        || polishHoliday(date)) {
      date = date.plusDays(1);
    }
    return date;
  }

  private boolean polishHoliday(LocalDate date) {
    Month month = date.getMonth();
    if ((month == Month.JANUARY && date.getDayOfMonth() == 1)
        || (month == Month.JANUARY && date.getDayOfMonth() == 6)
        || (month == Month.MAY && (date.getDayOfMonth() == 1 || date.getDayOfMonth() == 3))
        || (month == Month.AUGUST && date.getDayOfMonth() == 15)
        || (month == Month.NOVEMBER && (date.getDayOfMonth() == 1 || date.getDayOfMonth() == 11))
        || (month == Month.DECEMBER && (date.getDayOfMonth() == 25 || date.getDayOfMonth() == 26))) return true;
    int year = date.getYear();
    int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
    int f = (b + 8) / 25, g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
    LocalDate easter = LocalDate.of(year, (h + l - 7 * m + 114) / 31, (h + l - 7 * m + 114) % 31 + 1);
    return date.equals(easter.plusDays(1)) || date.equals(easter.plusDays(60));
  }

  public AccountingPaymentStatus paymentStatus(LocalDate dueDate, java.math.BigDecimal paid) {
    java.math.BigDecimal amount = paid == null ? java.math.BigDecimal.ZERO : paid;
    if (amount.signum() == 0) {
      return LocalDate.now(clock).isAfter(dueDate)
          ? AccountingPaymentStatus.OVERDUE : LocalDate.now(clock).isEqual(dueDate)
              ? AccountingPaymentStatus.DUE : AccountingPaymentStatus.NOT_DUE;
    }
    return amount.signum() < 0 ? AccountingPaymentStatus.PARTIAL : AccountingPaymentStatus.PAID;
  }
}
