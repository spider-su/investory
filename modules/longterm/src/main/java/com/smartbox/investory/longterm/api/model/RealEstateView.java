package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Factual result of a real-estate command or lookup. */
public record RealEstateView(
    Long id,
    Long portfolioId,
    String name,
    CurrencyType currency,
    LocalDate acquisitionDate,
    BigDecimal value,
    BigDecimal acquisitionValue,
    BigDecimal taxBase,
    String landRegisterNumber,
    boolean active,
    String notes) {
  public RealEstateView(
      Long id,
      Long portfolioId,
      String name,
      CurrencyType currency,
      LocalDate acquisitionDate,
      BigDecimal value,
      BigDecimal taxBase,
      String landRegisterNumber,
      boolean active,
      String notes) {
    this(
        id,
        portfolioId,
        name,
        currency,
        acquisitionDate,
        value,
        null,
        taxBase,
        landRegisterNumber,
        active,
        notes);
  }
}
