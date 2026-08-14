package com.smartbox.investory.services.portfolio.read;

import java.math.BigDecimal;

/** Composes attribution from canonical reporting components; does not recalculate P/L. */
public final class PerformanceAttributionCalculator {
  private PerformanceAttributionCalculator() {}

  public static PerformanceAttribution from(PerformanceResult result) {
    BigDecimal explained =
        nz(result.realizedProfit())
            .add(nz(result.dividends()))
            .add(nz(result.interest()))
            .subtract(nz(result.fees()))
            .subtract(nz(result.taxes()));
    BigDecimal residual = result.investmentResult().subtract(explained);
    boolean withinTolerance = residual.abs().compareTo(PerformanceAttribution.TOLERANCE) <= 0;
    return new PerformanceAttribution(
        result.realizedProfit(),
        null,
        result.dividends(),
        result.interest(),
        null,
        result.fees(),
        result.taxes(),
        residual,
        explained,
        withinTolerance,
        !withinTolerance);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
