package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentAssetClient implements InvestmentAssetClient {
  private final InvestmentAssetApi investmentAssetApi;

  public InProcessInvestmentAssetClient(
      @Qualifier("investmentAssetApplicationService") InvestmentAssetApi investmentAssetApi) {
    this.investmentAssetApi = investmentAssetApi;
  }

  @Override
  public AssetDetailView detail(String symbol, DashboardPeriod period) {
    return investmentAssetApi.detail(symbol, period);
  }

  @Override
  public java.util.List<AssetPricePointView> priceHistory(String symbol, DashboardPeriod period) {
    return investmentAssetApi.priceHistory(symbol, period);
  }

  @Override
  public java.util.List<DashboardPeriod> periods() {
    return investmentAssetApi.periods();
  }
}
