package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Factual result of a personal-asset command or lookup. */
public record PersonalAssetView(
    Long id,
    Long portfolioId,
    String name,
    PersonalAssetCategory category,
    CurrencyType currency,
    LocalDate acquisitionDate,
    BigDecimal value,
    boolean active,
    String notes) {}
