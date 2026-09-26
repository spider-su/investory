package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.util.List;

/** UI boundary for the in-process investment asset endpoint. */
public interface InvestmentAssetClient {
  AssetDetailView detail(Long portfolioId, String symbol, DashboardPeriod period);

  List<AssetPricePointView> priceHistory(Long portfolioId, String symbol, DashboardPeriod period);

  List<DashboardPeriod> periods();
}
