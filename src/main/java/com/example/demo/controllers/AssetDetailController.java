package com.example.demo.controllers;

import com.example.demo.services.dashboard.AssetDetailNotFoundException;
import com.example.demo.services.dashboard.AssetDetailService;
import com.example.demo.services.dashboard.AssetPriceChartService;
import com.example.demo.services.dashboard.DashboardPeriod;
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

  private final AssetDetailService assetDetailService;
  private final AssetPriceChartService assetPriceChartService;

  @GetMapping("/dashboard/assets/{symbol}")
  public String detail(
      @PathVariable String symbol, @RequestParam(required = false) String period, Model model) {
    DashboardPeriod selectedPeriod = DashboardPeriod.fromUrlValue(period);
    model.addAttribute("asset", assetDetailService.findBySymbol(symbol, selectedPeriod));
    model.addAttribute("priceHistory", assetPriceChartService.findBySymbol(symbol, selectedPeriod));
    model.addAttribute("periods", DashboardPeriod.values());
    return "dashboard/asset-detail";
  }

  @ExceptionHandler(AssetDetailNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String notFound(AssetDetailNotFoundException exception, Model model) {
    model.addAttribute("message", exception.getMessage());
    return "dashboard/asset-not-found";
  }
}
