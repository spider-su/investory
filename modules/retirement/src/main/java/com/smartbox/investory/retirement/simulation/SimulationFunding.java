package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Canonical funding and ending-balance facts for one simulated calendar year. */
public record SimulationFunding(
    BigDecimal fundingGap,
    BigDecimal reserveStart,
    BigDecimal reserveTransfer,
    BigDecimal reserveWithdrawal,
    BigDecimal reserveEnd,
    BigDecimal longTermFunding,
    BigDecimal longTermCapitalEnd,
    BigDecimal investmentStart,
    BigDecimal investmentReturn,
    BigDecimal investmentWithdrawal,
    BigDecimal investmentEnd,
    BigDecimal unfunded) {
  private static final BigDecimal ZERO = BigDecimal.ZERO;

  public SimulationFunding {
    fundingGap = value(fundingGap);
    reserveStart = value(reserveStart);
    reserveTransfer = value(reserveTransfer);
    reserveWithdrawal = value(reserveWithdrawal);
    reserveEnd = value(reserveEnd);
    longTermFunding = value(longTermFunding);
    longTermCapitalEnd = value(longTermCapitalEnd);
    investmentStart = value(investmentStart);
    investmentReturn = value(investmentReturn);
    investmentWithdrawal = value(investmentWithdrawal);
    investmentEnd = value(investmentEnd);
    unfunded = value(unfunded);
  }

  /** Capital moved into the reserve before the reserve withdrawal is applied. */
  public BigDecimal maturityToReserveOrOtherTransfer() {
    return reserveTransfer;
  }

  static SimulationFunding legacy() {
    return new SimulationFunding(
        ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);
  }

  private static BigDecimal value(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
