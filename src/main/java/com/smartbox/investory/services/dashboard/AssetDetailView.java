package com.smartbox.investory.services.dashboard;

import com.smartbox.investory.infrastructure.CurrencyType;
import java.time.ZonedDateTime;
import java.util.List;

public record AssetDetailView(
    Long id,
    String symbol,
    String name,
    String ticker,
    String yahooSymbol,
    String assetType,
    String country,
    CurrencyType currency,
    Double marketPrice,
    Double marketPriceUsd,
    String priceSource,
    ZonedDateTime priceUpdatedAt,
    List<AssetHoldingView> holdings,
    double totalQuantity,
    Double totalMarketValue,
    Double totalUnrealizedProfitLoss,
    List<AssetTransactionView> transactions,
    Double totalRealizedProfitLoss,
    List<AssetDividendView> dividends,
    double totalGrossDividends,
    double totalWithholdingTax,
    double totalNetDividends,
    DashboardPeriod period,
    AssetPerformanceView performance) {

  public boolean hasYahooSymbol() {
    return yahooSymbol != null && !yahooSymbol.isBlank();
  }

  public String yahooFinanceUrl() {
    return hasYahooSymbol() ? "https://finance.yahoo.com/quote/" + yahooSymbol : null;
  }

  public boolean hasHoldings() {
    return holdings != null && !holdings.isEmpty();
  }

  public boolean hasTransactions() {
    return transactions != null && !transactions.isEmpty();
  }

  public boolean hasDividends() {
    return dividends != null && !dividends.isEmpty();
  }

  public boolean hasPerformance() {
    return performance != null;
  }
}
