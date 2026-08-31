package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.reporting.DashboardPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequiredArgsConstructor
public class AssetDetailController {

  private final InvestmentAssetClient assets;

  @GetMapping("/dashboard/assets/{symbol}")
  public String detail(
      @PathVariable String symbol,
      @RequestParam(defaultValue = "YTD") DashboardPeriod period,
      Model model) {
    model.addAttribute("asset", assets.detail(symbol, period));
    model.addAttribute("priceHistory", assets.priceHistory(symbol, period));
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
