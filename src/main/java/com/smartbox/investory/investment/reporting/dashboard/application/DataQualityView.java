package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.accounting.model.models.PortfolioDataQualityIssue;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DataQualityView(
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
    LocalDate latestPriceDate,
    LocalDate latestFxMonth,
    OffsetDateTime latestReportingRefreshAt,
    List<PortfolioDataQualityIssue> issues) {

  public DataQualityView {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }
}
