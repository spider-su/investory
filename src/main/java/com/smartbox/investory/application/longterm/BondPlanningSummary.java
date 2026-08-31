package com.smartbox.investory.application.longterm;

import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
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
