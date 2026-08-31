package com.smartbox.investory.investment.api;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/** Typed operational data used by health, notification, and channel adapters. */
public interface InvestmentOperationalReader {
  PortfolioOperationsSnapshot portfolio();

  Optional<ImportOperationsSnapshot> latestImport();

  List<SymbolExposure> symbolExposures();

  record PortfolioOperationsSnapshot(
      String baseCurrency,
      BigDecimal balance,
      BigDecimal totalProfit,
      BigDecimal unrealizedProfit,
      BigDecimal realizedProfit,
      BigDecimal dividends,
      BigDecimal capitalGainsTax) {}

  record ImportOperationsSnapshot(
      long batchId,
      String broker,
      String status,
      ZonedDateTime startedAt,
      ZonedDateTime finishedAt) {}

  record SymbolExposure(String symbol, BigDecimal value, String currency) {}
}
