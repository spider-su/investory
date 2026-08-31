package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import com.smartbox.investory.investment.reporting.dashboard.service.AssetDetailNotFoundException;
import com.smartbox.investory.investment.reporting.dashboard.service.AssetDetailService;
import com.smartbox.investory.investment.reporting.dashboard.service.AssetPriceChartService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/** Composes the asset detail read model for the web adapter. */
@Service
@Primary
@RequiredArgsConstructor
public class InvestmentAssetApplicationService implements InvestmentAssetApi {
  private final AssetDetailService assetDetails;
  private final AssetPriceChartService priceHistory;

  @Override
  public AssetDetailView detail(String symbol, DashboardPeriod period) {
    try {
      return assetDetails.findBySymbol(
          symbol, period == null ? DashboardPeriod.YEAR_TO_DATE : period);
    } catch (AssetDetailNotFoundException exception) {
      throw new AssetNotFoundException(exception.getMessage(), exception);
    }
  }

  @Override
  public List<AssetPricePointView> priceHistory(String symbol, DashboardPeriod period) {
    return priceHistory.findBySymbol(
        symbol, period == null ? DashboardPeriod.YEAR_TO_DATE : period);
  }

  @Override
  public List<DashboardPeriod> periods() {
    return List.of(DashboardPeriod.values());
  }
}
