package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record BondCommand(
    Long portfolioId,
    Long id,
    String name,
    CurrencyType currency,
    BigDecimal value,
    LocalDate acquisitionDate,
    LocalDate maturityDate,
    InterestTreatment interestTreatment,
    BigDecimal annualRate,
    String notes) {}
