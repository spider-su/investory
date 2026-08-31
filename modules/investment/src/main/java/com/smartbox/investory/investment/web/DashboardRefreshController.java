package com.smartbox.investory.investment.web;

import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investment/maintenance")
@RequiredArgsConstructor
public class DashboardRefreshController {

  private final InvestmentMaintenanceApi maintenance;

  @PostMapping("/refresh-prices")
  public RefreshPricesResponse refreshPrices() {
    return RefreshPricesResponse.from(maintenance.refreshPrices());
  }

  @PostMapping("/refresh-currency")
  public InvestmentMaintenanceApi.CurrencyRefreshResult refreshCurrency() {
    return maintenance.refreshCurrency();
  }

  @PostMapping("/update-history")
  public RefreshPricesResponse updateHistory() {
    return RefreshPricesResponse.from(maintenance.updateHistory());
  }

  @PostMapping("/rebuild-monthly")
  public RefreshPricesResponse rebuildMonthly() {
    return RefreshPricesResponse.from(maintenance.rebuildMonthly());
  }

  @PostMapping("/assets/{symbol}/price")
  public com.smartbox.investory.investment.api.operations.ManualAssetPriceView
      updateManualAssetPrice(@PathVariable String symbol, @RequestBody ManualPriceRequest request) {
    return maintenance.updateManualAssetPrice(symbol, request.marketPrice());
  }

  public record RefreshPricesResponse(String status, String message, ZonedDateTime refreshedAt) {
    static RefreshPricesResponse from(InvestmentMaintenanceApi.MaintenanceResult result) {
      return new RefreshPricesResponse(result.status(), result.message(), result.refreshedAt());
    }
  }

  public record ManualPriceRequest(BigDecimal marketPrice) {}
}
