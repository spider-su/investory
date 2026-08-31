package com.smartbox.investory.investment.performance.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InstrumentPerformance {
  private String symbol;
  private double closedProfit;
  private double unrealizedProfit;
  private double total;
  private double dividends;

  /** Stored as a positive tax magnitude; displayed as a negative component. */
  private double withholdingTax;

  private double marketValue;
  private double costBasis;

  public InstrumentPerformance(
      String symbol, double closedProfit, double unrealizedProfit, double total) {
    this(symbol, closedProfit, unrealizedProfit, total, 0.0, 0.0, 0.0, 0.0);
  }
}
