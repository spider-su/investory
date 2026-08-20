package com.smartbox.investory.investment.reporting;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;

/** Canonical application read model for portfolio performance reporting. */
public record PerformanceResult(
    PerformancePeriod period,
    CurrencyType baseCurrency,
    BigDecimal startValue,
    BigDecimal endValue,
    BigDecimal contributions,
    BigDecimal withdrawals,
    BigDecimal netExternalFlows,
    BigDecimal investmentResult,
    BigDecimal realizedProfit,
    BigDecimal unrealizedProfit,
    BigDecimal dividends,
    BigDecimal interest,
    BigDecimal fees,
    BigDecimal taxes,
    BigDecimal returnPercentage,
    ReturnMetric timeWeightedReturn,
    ReturnMetric moneyWeightedReturn,
    PerformanceAttribution attribution) {}
