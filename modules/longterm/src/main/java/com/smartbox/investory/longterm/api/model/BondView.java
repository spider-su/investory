package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Factual result of a bond command or lookup. */
public record BondView(
    Long id,
    Long portfolioId,
    String name,
    CurrencyType currency,
    LocalDate acquisitionDate,
    BigDecimal value,
    BigDecimal interestRate,
    LocalDate maturityDate,
    boolean active,
    String notes) {}
