package com.smartbox.investory.investment.api.asset;

import java.util.List;

/** UI-facing read contract for investment asset detail pages. */
public interface InvestmentAssetApi {
  Object detail(String symbol, String period);

  Object priceHistory(String symbol, String period);

  List<?> periods();

  class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
