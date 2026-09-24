package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.investment.api.reporting.model.PerformanceAttribution;
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
    // Daily valuation facts currently cannot split price movement from FX movement. Keep that
    // combined, known component explicit instead of reporting it as an unexplained residual.
    BigDecimal marketAndFxMovement = nz(result.investmentResult()).subtract(explained);
    BigDecimal residual = BigDecimal.ZERO;
    BigDecimal totalAttributedResult = explained.add(marketAndFxMovement);
    return new PerformanceAttribution(
        result.realizedProfit(),
        null,
        result.dividends(),
        result.interest(),
        null,
        result.fees(),
        result.taxes(),
        residual,
        totalAttributedResult,
        true,
        false,
        marketAndFxMovement);
  }

  private static BigDecimal nz(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
