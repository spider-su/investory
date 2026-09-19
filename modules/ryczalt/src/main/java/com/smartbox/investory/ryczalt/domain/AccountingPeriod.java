package com.smartbox.investory.ryczalt.domain;

import java.time.YearMonth;
import java.util.Objects;

/** One accounting month and its canonical source-independent facts. */
public record AccountingPeriod(
    YearMonth period,
    PeriodStatus status,
    Bucket<Invoice> incomeInvoices,
    Bucket<Invoice> costInvoices,
    Bucket<Transaction> transactions,
    Bucket<Obligation> obligations) {
  public AccountingPeriod {
    period = Objects.requireNonNull(period, "period");
    status = Objects.requireNonNull(status, "status");
    incomeInvoices = Objects.requireNonNull(incomeInvoices, "incomeInvoices");
    costInvoices = Objects.requireNonNull(costInvoices, "costInvoices");
    transactions = Objects.requireNonNull(transactions, "transactions");
    obligations = Objects.requireNonNull(obligations, "obligations");
  }

  public static AccountingPeriod empty(YearMonth period) {
    return new AccountingPeriod(
        period, PeriodStatus.OPEN, Bucket.empty(), Bucket.empty(), Bucket.empty(), Bucket.empty());
  }
}
