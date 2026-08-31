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
    boolean residualMaterial) {
  public static final BigDecimal TOLERANCE = new BigDecimal("0.00000001");
}
