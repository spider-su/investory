package com.smartbox.investory.ryczalt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/** Canonical, source-independent bank or payment transaction. */
public record Transaction(String reference, LocalDate date, BigDecimal amount, Currency currency) {
  public Transaction {
    reference = Objects.requireNonNull(reference, "reference");
    date = Objects.requireNonNull(date, "date");
    amount = Objects.requireNonNull(amount, "amount");
    currency = Objects.requireNonNull(currency, "currency");
  }
}
