package com.smartbox.investory.retirement.planning;

import java.math.BigDecimal;

/** Display-only money values. The timeline source records remain canonical. */
public record PlanningTimelineMoney(
    BigDecimal annualCosts,
    BigDecimal rentalIncome,
    BigDecimal incomeGap,
    BigDecimal portfolioWithdrawal,
    BigDecimal cashReserve,
    BigDecimal bondsValue,
    BigDecimal bondsIncome,
    BigDecimal equityValue,
    BigDecimal equityGain,
    BigDecimal fixedIncome,
    BigDecimal equity) {
  public PlanningTimelineMoney(
      BigDecimal annualCosts,
      BigDecimal portfolioWithdrawal,
      BigDecimal fixedIncome,
      BigDecimal equity) {
    this(
        annualCosts,
        null,
        null,
        portfolioWithdrawal,
        null,
        null,
        null,
        equity,
        null,
        fixedIncome,
        equity);
  }
}
