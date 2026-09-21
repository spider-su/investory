package com.smartbox.investory.ryczalt.calculation.ryczalt;

import java.math.BigDecimal;
import java.util.Map;

/** Immutable ryczałt result suitable for a future frozen calculation record. */
public record RyczaltCalculationResult(
    BigDecimal revenue,
    Map<BigDecimal, BigDecimal> revenueByRate,
    BigDecimal socialContributionDeduction,
    BigDecimal healthContributionPaid,
    BigDecimal healthDeduction,
    BigDecimal deductionsAvailable,
    BigDecimal deductionsUsed,
    BigDecimal deductionsCarryForward,
    Map<BigDecimal, BigDecimal> taxableByRate,
    Map<BigDecimal, BigDecimal> taxByRate,
    BigDecimal taxableBase,
    BigDecimal calculatedTax,
    String ruleVersion) {
  public RyczaltCalculationResult {
    revenueByRate = Map.copyOf(revenueByRate);
    taxableByRate = Map.copyOf(taxableByRate);
    taxByRate = Map.copyOf(taxByRate);
  }
}
