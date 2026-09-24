package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.web.InvestmentAssetRestController;
import org.springframework.stereotype.Component;

@Component
public class InProcessInvestmentAssetClient implements InvestmentAssetClient {
  private final InvestmentAssetRestController rest;

  public InProcessInvestmentAssetClient(InvestmentAssetRestController rest) {
    this.rest = rest;
  }

  @Override
  public AssetDetailView detail(Long portfolioId, String symbol, DashboardPeriod period) {
    return rest.detail(portfolioId, symbol, period);
  }

  @Override
  public java.util.List<AssetPricePointView> priceHistory(
      Long portfolioId, String symbol, DashboardPeriod period) {
    return rest.priceHistory(portfolioId, symbol, period);
  }

  @Override
  public java.util.List<DashboardPeriod> periods() {
    return rest.periods();
  }
}
