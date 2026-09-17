package com.smartbox.investory.accounting;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
    int day = "VAT".equals(obligationType) || "VAT_UE".equals(obligationType) ? 25 : 20;
    LocalDate date = period.plusMonths(1).withDayOfMonth(day);
    while (date.getDayOfWeek() == DayOfWeek.SATURDAY
        || date.getDayOfWeek() == DayOfWeek.SUNDAY
        || AccountingDateRules.isPolishHoliday(date)) {
      date = date.plusDays(1);
    }
    return date;
  }

  public AccountingPaymentStatus paymentStatus(LocalDate dueDate, java.math.BigDecimal paid) {
    java.math.BigDecimal amount = paid == null ? java.math.BigDecimal.ZERO : paid;
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Paid amount cannot be negative");
    }
    if (amount.signum() == 0) {
      return LocalDate.now(clock).isAfter(dueDate)
          ? AccountingPaymentStatus.OVERDUE
          : LocalDate.now(clock).isEqual(dueDate)
              ? AccountingPaymentStatus.DUE
              : AccountingPaymentStatus.NOT_DUE;
    }
    return amount.signum() < 0 ? AccountingPaymentStatus.PARTIAL : AccountingPaymentStatus.PAID;
  }

  /** Compares observed payment with the canonical obligation amount. */
  public AccountingPaymentStatus paymentStatus(BigDecimal expected, BigDecimal paid) {
    BigDecimal obligation = expected == null ? BigDecimal.ZERO : expected;
    BigDecimal amount = paid == null ? BigDecimal.ZERO : paid;
    if (amount.signum() < 0) throw new IllegalArgumentException("Paid amount cannot be negative");
    if (obligation.signum() <= 0) return AccountingPaymentStatus.PAID;
    if (amount.signum() == 0) return AccountingPaymentStatus.NOT_PAID;
    if (amount.compareTo(obligation) < 0) return AccountingPaymentStatus.PARTIAL;
    return AccountingPaymentStatus.PAID;
  }

  public BigDecimal remainingAmount(BigDecimal expected, BigDecimal paid) {
    BigDecimal obligation = expected == null ? BigDecimal.ZERO : expected;
    BigDecimal amount = paid == null ? BigDecimal.ZERO : paid;
    if (amount.signum() < 0) throw new IllegalArgumentException("Paid amount cannot be negative");
    if (obligation.signum() <= 0) return BigDecimal.ZERO;
    return obligation.subtract(amount).max(BigDecimal.ZERO);
  }
}
