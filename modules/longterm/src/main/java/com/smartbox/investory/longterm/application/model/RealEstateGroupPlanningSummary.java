package com.smartbox.investory.longterm.application.model;

import java.math.BigDecimal;

/** Real-estate-only group metrics displayed on the long-term assets page. */
public record RealEstateGroupPlanningSummary(
    BigDecimal totalPaymentMonthly,
    BigDecimal netMonthlyIncome,
    BigDecimal monthlyRentTax,
    BigDecimal incomeYield) {}
