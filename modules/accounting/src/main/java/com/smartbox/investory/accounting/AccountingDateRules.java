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
        || result.getDayOfWeek() == DayOfWeek.SUNDAY) {
      result = result.minusDays(1);
    }
    return result;
  }
}
