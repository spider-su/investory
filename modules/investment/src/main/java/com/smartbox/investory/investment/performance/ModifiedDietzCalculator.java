package com.smartbox.investory.investment.performance;

import java.math.BigDecimal;
import java.util.Collection;

/** Shared daily Modified Dietz calculation using beginning-of-day flow weights. */
public final class ModifiedDietzCalculator {

  private ModifiedDietzCalculator() {}

  public static double profit(double openingEquity, double closingEquity, double netFlow) {
    return closingEquity - openingEquity - netFlow;
  }

  public static BigDecimal profit(
      BigDecimal openingEquity, BigDecimal closingEquity, BigDecimal netFlow) {
    return closingEquity.subtract(openingEquity).subtract(netFlow);
  }

  public static BigDecimal returnRate(
      BigDecimal openingEquity, BigDecimal closingEquity, Collection<BigDecimal> signedFlows) {
    BigDecimal netFlow = signedFlows.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal denominator = openingEquity.add(netFlow);
    return denominator.abs().compareTo(new BigDecimal("0.000000001")) <= 0
        ? BigDecimal.ZERO
        : profit(openingEquity, closingEquity, netFlow)
            .divide(denominator, 16, java.math.RoundingMode.HALF_UP);
  }

  public static double returnRate(
      double openingEquity, double closingEquity, Collection<Double> signedFlows) {
    double netFlow = signedFlows.stream().mapToDouble(Double::doubleValue).sum();
    double denominator = openingEquity + netFlow;
    return Math.abs(denominator) <= 1e-9
        ? 0.0
        : profit(openingEquity, closingEquity, netFlow) / denominator;
  }
}
