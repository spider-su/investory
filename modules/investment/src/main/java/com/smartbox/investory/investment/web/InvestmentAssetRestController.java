package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.asset.InvestmentAssetApi;
import com.smartbox.investory.investment.api.asset.model.AssetDetailView;
import com.smartbox.investory.investment.api.asset.model.AssetPricePointView;
import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST and in-process Java facade for Investment asset details. */
@RestController
@RequestMapping("/api/v1")
@Validated
@RequiredArgsConstructor
public class InvestmentAssetRestController {
  private final InvestmentAssetApi assets;

  @GetMapping("/portfolios/{portfolioId}/investment/assets/{symbol}")
  public AssetDetailView detail(
      @PathVariable @Positive Long portfolioId,
      @PathVariable String symbol,
      @RequestParam(defaultValue = "YTD") DashboardPeriod period) {
    requirePositivePortfolio(portfolioId);
    return assets.detail(portfolioId, symbol, period);
  }

  @GetMapping("/portfolios/{portfolioId}/investment/assets/{symbol}/price-history")
  public List<AssetPricePointView> priceHistory(
      @PathVariable @Positive Long portfolioId,
      @PathVariable String symbol,
      @RequestParam(defaultValue = "YTD") DashboardPeriod period) {
    requirePositivePortfolio(portfolioId);
    return assets.priceHistory(portfolioId, symbol, period);
  }

  @GetMapping("/investment/assets/periods")
  public List<DashboardPeriod> periods() {
    return assets.periods();
  }

  private static void requirePositivePortfolio(Long portfolioId) {
    if (portfolioId == null || portfolioId <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "portfolioId must be positive");
    }
  }
}
