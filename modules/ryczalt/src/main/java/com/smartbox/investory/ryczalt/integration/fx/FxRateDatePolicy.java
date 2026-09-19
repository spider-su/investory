package com.smartbox.investory.ryczalt.integration.fx;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Selects the accounting rate date independently from the external provider. */
public final class FxRateDatePolicy {
  private FxRateDatePolicy() {}

  /** Returns the prior business day, preserving the certified Accounting convention. */
  public static LocalDate priorBusinessDay(LocalDate accountingDate) {
    LocalDate date = accountingDate.minusDays(1);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      date = date.minusDays(1);
    }
    return date;
  }
}
