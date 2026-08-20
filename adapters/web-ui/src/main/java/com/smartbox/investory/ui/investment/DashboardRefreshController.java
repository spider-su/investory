package com.smartbox.investory.ui.investment;

import com.smartbox.investory.investment.api.InvestmentMaintenanceApi;
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

  private final InvestmentMaintenanceApi maintenance;

  @PostMapping("/refresh-prices")
  RefreshPricesResponse refreshPrices() {
    return RefreshPricesResponse.from(maintenance.refreshPrices());
  }

  @PostMapping("/refresh-currency")
  Object refreshCurrency() {
    return maintenance.refreshCurrency();
  }

  @PostMapping("/update-history")
  RefreshPricesResponse updateHistory() {
    return RefreshPricesResponse.from(maintenance.updateHistory());
  }

  @PostMapping("/rebuild-monthly")
  RefreshPricesResponse rebuildMonthly() {
    return RefreshPricesResponse.from(maintenance.rebuildMonthly());
  }

  @PostMapping("/assets/{symbol}/price")
  Object updateManualAssetPrice(
      @PathVariable String symbol, @RequestBody ManualPriceRequest request) {
    return maintenance.updateManualAssetPrice(symbol, request.marketPrice());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ErrorResponse handleBadRequest(IllegalArgumentException exception) {
    return new ErrorResponse(exception.getMessage());
  }

  public record RefreshPricesResponse(String status, String message, ZonedDateTime refreshedAt) {
    static RefreshPricesResponse from(InvestmentMaintenanceApi.MaintenanceResult result) {
      return new RefreshPricesResponse(result.status(), result.message(), result.refreshedAt());
    }
  }

  public record ManualPriceRequest(double marketPrice) {}

  public record ErrorResponse(String message) {}
}
