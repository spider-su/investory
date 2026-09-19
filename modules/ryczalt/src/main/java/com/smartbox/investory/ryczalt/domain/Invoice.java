package com.smartbox.investory.ryczalt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/** Canonical, source-independent invoice. Direction comes from its period bucket. */
public record Invoice(
    String reference,
    LocalDate issueDate,
    LocalDate accountingDate,
    BigDecimal netAmount,
    BigDecimal vatAmount,
    BigDecimal grossAmount,
    Currency currency,
    BigDecimal bookedNetPln,
    BigDecimal ryczaltRate,
    BigDecimal deductibleVat) {
  public Invoice {
    reference = Objects.requireNonNull(reference, "reference");
    issueDate = Objects.requireNonNull(issueDate, "issueDate");
    accountingDate = Objects.requireNonNull(accountingDate, "accountingDate");
    netAmount = Objects.requireNonNull(netAmount, "netAmount");
    vatAmount = Objects.requireNonNull(vatAmount, "vatAmount");
    grossAmount = Objects.requireNonNull(grossAmount, "grossAmount");
    currency = Objects.requireNonNull(currency, "currency");
  }

  public Invoice(
      String reference,
      LocalDate issueDate,
      LocalDate accountingDate,
      BigDecimal netAmount,
      BigDecimal vatAmount,
      BigDecimal grossAmount,
      Currency currency) {
    this(
        reference,
        issueDate,
        accountingDate,
        netAmount,
        vatAmount,
        grossAmount,
        currency,
        null,
        null,
        null);
  }
}
