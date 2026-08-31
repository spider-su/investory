package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record DepositCommand(
    Long portfolioId,
    String name,
    CurrencyType currency,
    BigDecimal value,
    LocalDate acquisitionDate,
    LocalDate maturityDate,
    InterestTreatment interestTreatment,
    BigDecimal annualInterestRate,
    BigDecimal taxRate,
    String notes) {}
