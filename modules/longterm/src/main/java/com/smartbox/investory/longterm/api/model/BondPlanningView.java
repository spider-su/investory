package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Public Long-Term API model. */
public record BondPlanningView(
    BigDecimal value,
    BigDecimal annualRate,
    BigDecimal grossInterest,
    BigDecimal annualTax,
    BigDecimal netInterest,
    BigDecimal netYield,
    LocalDate maturityDate,
    InterestTreatment interestTreatment) {}
