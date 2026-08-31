package com.smartbox.investory.investment.api.asset;

import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.util.List;

/** UI-facing read contract for investment asset detail pages. */
public interface InvestmentAssetApi {
  AssetDetailView detail(String symbol, DashboardPeriod period);

  List<AssetPricePointView> priceHistory(String symbol, DashboardPeriod period);

  List<DashboardPeriod> periods();

  class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
