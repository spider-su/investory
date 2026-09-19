package com.smartbox.investory.ryczalt.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

public record Obligation(
    ObligationType type,
    BigDecimal amount,
    Currency currency,
    LocalDate dueDate,
    ObligationStatus status) {
  public Obligation {
    type = Objects.requireNonNull(type, "type");
    amount = amount == null ? BigDecimal.ZERO : amount;
    currency = currency == null ? Currency.getInstance("PLN") : currency;
    status = status == null ? ObligationStatus.OPEN : status;
  }

  public Obligation(ObligationType type) {
    this(type, BigDecimal.ZERO, Currency.getInstance("PLN"), null, ObligationStatus.OPEN);
  }
}
