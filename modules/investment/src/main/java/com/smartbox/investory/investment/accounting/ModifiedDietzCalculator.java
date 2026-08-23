package com.smartbox.investory.investment.accounting;

import java.util.Collection;

/** Shared daily Modified Dietz calculation using beginning-of-day flow weights. */
public final class ModifiedDietzCalculator {

  private ModifiedDietzCalculator() {}

  public static double profit(double openingEquity, double closingEquity, double netFlow) {
    return closingEquity - openingEquity - netFlow;
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
