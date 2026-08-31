package com.smartbox.investory.longterm.api.model;

import java.math.BigDecimal;

/** Public Long-Term API model. */
public record RealEstateGroupPlanningView(
    BigDecimal totalPaymentMonthly,
    BigDecimal netMonthlyIncome,
    BigDecimal monthlyReduce,
    BigDecimal taxBase,
    BigDecimal monthlyTax,
    BigDecimal netYield) {}
