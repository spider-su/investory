package com.smartbox.investory.ryczalt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/** Canonical, source-independent bank or payment transaction. */
public record Transaction(
    String reference,
    LocalDate date,
    BigDecimal amount,
    Currency currency,
    String counterparty,
    String counterpartyAccount,
    String description) {
  public Transaction {
    reference = Objects.requireNonNull(reference, "reference");
    date = Objects.requireNonNull(date, "date");
    amount = Objects.requireNonNull(amount, "amount");
    currency = Objects.requireNonNull(currency, "currency");
  }

  public Transaction(String reference, LocalDate date, BigDecimal amount, Currency currency) {
    this(reference, date, amount, currency, null, null, null);
  }

  public Transaction(
      String reference,
      LocalDate date,
      BigDecimal amount,
      Currency currency,
      String counterparty,
      String description) {
    this(reference, date, amount, currency, counterparty, null, description);
  }
}
