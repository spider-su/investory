package com.smartbox.investory.retirement.api.model;

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
    BigDecimal equityHarvestToReserve,
    BigDecimal unfunded,
    BigDecimal capitalizedBondReturn) {
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
    equityHarvestToReserve = value(equityHarvestToReserve);
    unfunded = value(unfunded);
    capitalizedBondReturn = value(capitalizedBondReturn);
  }

  public SimulationFunding(
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
    this(
        fundingGap,
        reserveStart,
        reserveTransfer,
        reserveWithdrawal,
        reserveEnd,
        longTermFunding,
        longTermCapitalEnd,
        investmentStart,
        investmentReturn,
        investmentWithdrawal,
        investmentEnd,
        BigDecimal.ZERO,
        unfunded,
        BigDecimal.ZERO);
  }

  /** Capital moved into the reserve before the reserve withdrawal is applied. */
  public BigDecimal maturityToReserveOrOtherTransfer() {
    return reserveTransfer;
  }

  /** Domain-neutral name for the Investment-to-reserve capital transfer. */
  public BigDecimal investmentHarvestToReserve() {
    return equityHarvestToReserve;
  }

  private static BigDecimal value(BigDecimal value) {
    return value == null ? ZERO : value;
  }
}
