package com.smartbox.investory.retirement.api.model;

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
    BigDecimal investmentEnd,
    BigDecimal cashStart,
    BigDecimal cashEnd,
    BigDecimal bondsStart,
    BigDecimal bondsEnd,
    BigDecimal equitiesStart,
    BigDecimal equitiesEnd,
    BigDecimal realEstateStart,
    BigDecimal realEstateEnd,
    BigDecimal cashWithdrawal,
    BigDecimal bondWithdrawal,
    BigDecimal equityWithdrawal,
    BigDecimal realEstateWithdrawal,
    BigDecimal bondReturn,
    BigDecimal equityReturn,
    BigDecimal equityRefill,
    BigDecimal employmentIncome,
    BigDecimal pensionIncome,
    BigDecimal eventIncome,
    BigDecimal funded,
    BigDecimal fundingSurplus,
    BigDecimal incomeUsed,
    BigDecimal capitalFunding) {

  /** Net internal transfer received by Bonds; positive means Bonds receive value. */
  public BigDecimal bondTransfer() {
    return equityRefill;
  }

  /** Net internal transfer received by Equities; negative means value leaves Equities. */
  public BigDecimal equityTransfer() {
    return equityRefill == null ? null : equityRefill.negate();
  }

  /** Binary/source compatibility for already running web-ui classes during hot reload. */
  public PlanningTimelineMoney(
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
      BigDecimal investmentEnd) {
    this(
        annualCosts,
        totalIncome,
        rentalIncome,
        bondIncome,
        fundingGap,
        reserveWithdrawal,
        longTermFunding,
        investmentWithdrawal,
        unfunded,
        reserveEnd,
        longTermCapitalEnd,
        investmentEnd,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /** Source compatibility for callers compiled against the pre-source-break result shape. */
  public PlanningTimelineMoney(
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
      BigDecimal investmentEnd,
      BigDecimal cashStart,
      BigDecimal cashEnd,
      BigDecimal bondsStart,
      BigDecimal bondsEnd,
      BigDecimal equitiesStart,
      BigDecimal equitiesEnd,
      BigDecimal realEstateStart,
      BigDecimal realEstateEnd,
      BigDecimal cashWithdrawal,
      BigDecimal bondWithdrawal,
      BigDecimal equityWithdrawal,
      BigDecimal realEstateWithdrawal,
      BigDecimal bondReturn,
      BigDecimal equityReturn,
      BigDecimal equityRefill) {
    this(
        annualCosts,
        totalIncome,
        rentalIncome,
        bondIncome,
        fundingGap,
        reserveWithdrawal,
        longTermFunding,
        investmentWithdrawal,
        unfunded,
        reserveEnd,
        longTermCapitalEnd,
        investmentEnd,
        cashStart,
        cashEnd,
        bondsStart,
        bondsEnd,
        equitiesStart,
        equitiesEnd,
        realEstateStart,
        realEstateEnd,
        cashWithdrawal,
        bondWithdrawal,
        equityWithdrawal,
        realEstateWithdrawal,
        bondReturn,
        equityReturn,
        equityRefill,
        null,
        null,
        null,
        annualCosts == null || totalIncome == null
            ? null
            : totalIncome
                .add(zero(reserveWithdrawal))
                .add(zero(longTermFunding))
                .add(zero(investmentWithdrawal)),
        totalIncome == null || annualCosts == null
            ? null
            : totalIncome.subtract(annualCosts).max(BigDecimal.ZERO),
        totalIncome == null || annualCosts == null
            ? null
            : totalIncome.min(annualCosts).max(BigDecimal.ZERO),
        zero(reserveWithdrawal).add(zero(longTermFunding)).add(zero(investmentWithdrawal)));
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
