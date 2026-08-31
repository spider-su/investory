package com.smartbox.investory.investment.api.operations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/** UI-facing maintenance commands for market and derived investment data. */
public interface InvestmentMaintenanceApi {
  MaintenanceResult refreshPrices();

  CurrencyRefreshResult refreshCurrency();

  MaintenanceResult updateHistory();

  MaintenanceResult rebuildMonthly();

  ManualAssetPriceView updateManualAssetPrice(String symbol, BigDecimal marketPrice);

  record MaintenanceResult(String status, String message, ZonedDateTime refreshedAt) {}

  record CurrencyRefreshResult(LocalDate rateDate, List<String> updated, List<String> failed) {
    public CurrencyRefreshResult {
      updated = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(updated);
      failed = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(failed);
    }
  }

  class InvalidMaintenanceRequest extends RuntimeException {
    public InvalidMaintenanceRequest(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
