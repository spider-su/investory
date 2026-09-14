package com.smartbox.investory.accounting;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;

/** Shared date rules for accounting acquisition and calculation. */
public final class AccountingDateRules {
  private AccountingDateRules() {}

  public static LocalDate accountingPeriod(
      LocalDate saleDate, LocalDate issueDate, LocalDate paymentDate, boolean correction) {
    if (correction && issueDate != null) return issueDate.withDayOfMonth(1);
    return Stream.of(saleDate, issueDate, paymentDate)
        .filter(Objects::nonNull)
        .min(LocalDate::compareTo)
        .orElseThrow(() -> new IllegalArgumentException("At least one accounting date is required"))
        .withDayOfMonth(1);
  }

  public static LocalDate priorBusinessDay(LocalDate date) {
    if (date == null) return null;
    LocalDate result = date.minusDays(1);
    while (result.getDayOfWeek() == DayOfWeek.SATURDAY
        || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
      result = result.minusDays(1);
    }
    return result;
  }
}
