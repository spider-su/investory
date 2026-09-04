package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@Validated
@RequiredArgsConstructor
public class AssetDetailController {

  private final InvestmentAssetClient assets;

  @GetMapping("/portfolios/{portfolioId}/dashboard/assets/{symbol}")
  public String detail(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "YTD") DashboardPeriod period,
      @org.springframework.web.bind.annotation.PathVariable @Positive Long portfolioId,
      Model model) {
    model.addAttribute("asset", assets.detail(portfolioId, symbol, period));
    model.addAttribute("priceHistory", assets.priceHistory(portfolioId, symbol, period));
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("periods", assets.periods());
    return "dashboard/asset-detail";
  }

  @ExceptionHandler(
      com.smartbox.investory.investment.api.asset.InvestmentAssetApi.AssetNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String notFound() {
    return "dashboard/asset-not-found";
  }
}
