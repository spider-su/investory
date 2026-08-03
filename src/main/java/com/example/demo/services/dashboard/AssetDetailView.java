package com.example.demo.services.dashboard;

import com.example.demo.infrastructure.CurrencyType;
import java.time.ZonedDateTime;

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
    ZonedDateTime priceUpdatedAt) {

  public boolean hasYahooSymbol() {
    return yahooSymbol != null && !yahooSymbol.isBlank();
  }

  public String yahooFinanceUrl() {
    return hasYahooSymbol() ? "https://finance.yahoo.com/quote/" + yahooSymbol : null;
  }
}
