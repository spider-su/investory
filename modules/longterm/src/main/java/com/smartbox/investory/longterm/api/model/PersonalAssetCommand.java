package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PersonalAssetCommand(
    Long portfolioId,
    Long id,
    String name,
    PersonalAssetCategory category,
    CurrencyType currency,
    BigDecimal value,
    LocalDate acquisitionDate,
    String notes) {}
