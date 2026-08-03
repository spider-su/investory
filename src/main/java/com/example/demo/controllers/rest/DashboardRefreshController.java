package com.example.demo.controllers.rest;

import com.example.demo.services.ManualAssetPriceService;
import com.example.demo.services.ManualAssetPriceService.ManualAssetPrice;
import com.example.demo.services.MarketService;
import com.example.demo.services.PortfolioProjectionService;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DashboardRefreshController {

  private final MarketService marketService;
  private final ManualAssetPriceService manualAssetPriceService;
  private final PortfolioProjectionService portfolioProjectionService;

  @PostMapping("/refresh-prices")
  RefreshPricesResponse refreshPrices() {
    marketService.fullPortfolioUpdate();
    return new RefreshPricesResponse("OK", "Open position prices refreshed", ZonedDateTime.now());
  }

  @PostMapping("/update-history")
  RefreshPricesResponse updateHistory() {
    marketService.refreshMarketPricesAndPositions();
    portfolioProjectionService.recalculateAll();
    return new RefreshPricesResponse(
        "OK", "Market prices refreshed and history rebuilt", ZonedDateTime.now());
  }

  @PostMapping("/rebuild-monthly")
  RefreshPricesResponse rebuildMonthly() {
    portfolioProjectionService.recalculateAll();
    return new RefreshPricesResponse("OK", "Account stats rebuilt", ZonedDateTime.now());
  }

  @PostMapping("/assets/{symbol}/price")
  ManualAssetPrice updateManualAssetPrice(
      @PathVariable String symbol, @RequestBody ManualPriceRequest request) {
    return manualAssetPriceService.updatePrice(symbol, request.marketPrice());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse handleBadRequest(IllegalArgumentException exception) {
    return new ErrorResponse(exception.getMessage());
  }

  public record RefreshPricesResponse(String status, String message, ZonedDateTime refreshedAt) {}

  public record ManualPriceRequest(double marketPrice) {}

  public record ErrorResponse(String message) {}
}
