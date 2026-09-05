package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit real-estate asset command. Rental contracts are separate commands. */
public record RealEstateEntryModel(
    Long portfolioId,
    Long id,
    String name,
    CurrencyType currency,
    BigDecimal value,
    BigDecimal taxBase,
    LocalDate acquisitionDate,
    String landRegisterNumber,
    String notes) {}
