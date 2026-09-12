package com.smartbox.investory.accounting;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** Due dates for the currently supported monthly Polish JDG POC. */
@Component
public class AccountingDueDatePolicy {
  public LocalDate dueDate(LocalDate period, String obligationType) {
    int day = "VAT".equals(obligationType) ? 25 : 20;
    return period.plusMonths(1).withDayOfMonth(day);
  }
}
