package com.smartbox.investory.accounting;

import java.time.LocalDate;

/** A tax month represented consistently as its first calendar day. */
public record AccountingTaxPeriod(LocalDate start) {
  public AccountingTaxPeriod {
    if (start == null) throw new IllegalArgumentException("Tax period is required");
    if (start.getDayOfMonth() != 1) {
      throw new IllegalArgumentException("Tax period must start on the first day of a month");
    }
  }

  public static AccountingTaxPeriod of(LocalDate date) {
    return new AccountingTaxPeriod(date);
  }
}
