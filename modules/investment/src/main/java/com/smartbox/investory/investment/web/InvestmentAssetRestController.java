package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST and in-process Java facade for Investment asset details. */
@RestController
@RequestMapping("/api/v1/investment/assets")
@RequiredArgsConstructor
public class InvestmentAssetRestController {
  private final InvestmentAssetApi assets;

  @GetMapping("/{symbol}")
  public AssetDetailView detail(
      @PathVariable String symbol, @RequestParam(defaultValue = "YTD") DashboardPeriod period) {
    return assets.detail(symbol, period);
  }

  @GetMapping("/{symbol}/price-history")
  public List<AssetPricePointView> priceHistory(
      @PathVariable String symbol, @RequestParam(defaultValue = "YTD") DashboardPeriod period) {
    return assets.priceHistory(symbol, period);
  }

  @GetMapping("/periods")
  public List<DashboardPeriod> periods() {
    return assets.periods();
  }
}
