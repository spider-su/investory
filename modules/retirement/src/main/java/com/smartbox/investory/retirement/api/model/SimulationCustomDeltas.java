package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

/** User-supplied CUSTOM scenario adjustments, stored as rate deltas (1 pp = 0.01). */
public record SimulationCustomDeltas(
    BigDecimal inflation,
    BigDecimal rentalGrowth,
    BigDecimal bondReturn,
    BigDecimal equityReturn,
    BigDecimal spendingGrowth) {
  public SimulationCustomDeltas {
    inflation = value(inflation);
    rentalGrowth = value(rentalGrowth);
    bondReturn = value(bondReturn);
    equityReturn = value(equityReturn);
    spendingGrowth = value(spendingGrowth);
  }

  public static SimulationCustomDeltas zero() {
    return new SimulationCustomDeltas(
        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
  }

  public boolean isZero() {
    return inflation.signum() == 0
        && rentalGrowth.signum() == 0
        && bondReturn.signum() == 0
        && equityReturn.signum() == 0
        && spendingGrowth.signum() == 0;
  }

  private static BigDecimal value(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
