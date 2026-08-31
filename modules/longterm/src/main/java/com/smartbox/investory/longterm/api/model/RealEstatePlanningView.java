package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Public Long-Term API model. */
public record RealEstatePlanningView(
    BigDecimal taxBase,
    BigDecimal totalPaymentMonthly,
    BigDecimal monthlyIncome,
    BigDecimal monthlyReduce,
    BigDecimal annualTax,
    BigDecimal netMonthlyIncome,
    BigDecimal incomeYield) {}
