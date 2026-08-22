package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;

/** Display-only canonical cash-flow, funding, and ending-balance facts for a timeline row. */
public record PlanningTimelineMoney(
    BigDecimal annualCosts,
    BigDecimal totalIncome,
    BigDecimal rentalIncome,
    BigDecimal bondIncome,
    BigDecimal fundingGap,
    BigDecimal reserveWithdrawal,
    BigDecimal longTermFunding,
    BigDecimal investmentWithdrawal,
    BigDecimal unfunded,
    BigDecimal reserveEnd,
    BigDecimal longTermCapitalEnd,
    BigDecimal investmentEnd) {}
