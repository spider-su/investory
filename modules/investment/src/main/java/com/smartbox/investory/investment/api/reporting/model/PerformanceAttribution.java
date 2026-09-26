package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;

/** Explainable investment-result components for one canonical performance period. */
public record PerformanceAttribution(
    BigDecimal realizedProfitLoss,
    BigDecimal unrealizedProfitLoss,
    BigDecimal dividends,
    BigDecimal interest,
    BigDecimal fxEffect,
    BigDecimal fees,
    BigDecimal taxes,
    BigDecimal residual,
    BigDecimal totalAttributedResult,
    boolean reconcilesWithinTolerance,
    boolean residualMaterial,
    BigDecimal marketAndFxMovement) {
  public static final BigDecimal TOLERANCE = new BigDecimal("0.00000001");

  /** Compatibility constructor for callers that do not provide the combined market/FX component. */
  public PerformanceAttribution(
      BigDecimal realizedProfitLoss,
      BigDecimal unrealizedProfitLoss,
      BigDecimal dividends,
      BigDecimal interest,
      BigDecimal fxEffect,
      BigDecimal fees,
      BigDecimal taxes,
      BigDecimal residual,
      BigDecimal totalAttributedResult,
      boolean reconcilesWithinTolerance,
      boolean residualMaterial) {
    this(
        realizedProfitLoss,
        unrealizedProfitLoss,
        dividends,
        interest,
        fxEffect,
        fees,
        taxes,
        residual,
        totalAttributedResult,
        reconcilesWithinTolerance,
        residualMaterial,
        null);
  }
}
