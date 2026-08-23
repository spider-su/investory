package com.smartbox.investory.longterm.application.model;

import com.smartbox.investory.longterm.infrastructure.InterestTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Current bond planning facts, separate from normalized annual economics. */
public record BondPlanningSummary(
    BigDecimal value,
    BigDecimal annualRate,
    BigDecimal grossInterest,
    BigDecimal annualTax,
    BigDecimal netInterest,
    BigDecimal netYield,
    LocalDate maturityDate,
    InterestTreatment interestTreatment) {}
