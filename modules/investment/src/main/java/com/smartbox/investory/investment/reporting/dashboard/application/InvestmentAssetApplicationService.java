package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.api.InvestmentAssetApi;
import com.smartbox.investory.investment.reporting.dashboard.service.AssetDetailNotFoundException;
import com.smartbox.investory.investment.reporting.dashboard.service.AssetDetailService;
import com.smartbox.investory.investment.reporting.dashboard.service.AssetPriceChartService;
import com.smartbox.investory.investment.reporting.dashboard.service.DashboardPeriod;
import java.util.List;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Composes the asset detail read model for the web adapter. */
@Service
@RequiredArgsConstructor
public class InvestmentAssetApplicationService implements InvestmentAssetApi {
  private final AssetDetailService assetDetails;
  private final AssetPriceChartService priceHistory;

  @Override
  public Object detail(String symbol, String period) {
    try {
      return assetDetails.findBySymbol(symbol, DashboardPeriod.fromUrlValue(period));
    } catch (AssetDetailNotFoundException exception) {
      throw new AssetNotFoundException(exception.getMessage(), exception);
    }
  }

  @Override
  public Object priceHistory(String symbol, String period) {
    return priceHistory.findBySymbol(symbol, DashboardPeriod.fromUrlValue(period));
  }

  @Override
  public List<?> periods() {
    return Arrays.asList(DashboardPeriod.values());
  }
}
