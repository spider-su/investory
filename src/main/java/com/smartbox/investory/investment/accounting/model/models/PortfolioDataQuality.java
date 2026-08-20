package com.smartbox.investory.investment.accounting.model.models;

import java.time.OffsetDateTime;

public record PortfolioDataQuality(
    String state,
    long reconciledAccounts,
    long totalAccounts,
    long pricedOpenPositions,
    long totalOpenPositions,
    long missingPriceCount,
    long stalePriceCount,
    long proxyPriceCount,
    long estimatedPriceCount,
    long missingFxCount,
    long ambiguousCostBasisCurrencyCount,
    long unclassifiedCashOperationCount,
    OffsetDateTime latestBrokerReconciliationAt,
    OffsetDateTime latestImportAt,
    java.time.LocalDate latestPriceDate,
    java.time.LocalDate latestFxMonth,
    OffsetDateTime latestReportingRefreshAt,
    java.util.List<PortfolioDataQualityIssue> issues) {
  public static PortfolioDataQuality unknown() {
    return new PortfolioDataQuality(
        "CRITICAL",
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        null,
        java.util.List.of());
  }
}
