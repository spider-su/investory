package com.smartbox.investory.investment.api;

import java.time.ZonedDateTime;

/** UI-facing maintenance commands for market and derived investment data. */
public interface InvestmentMaintenanceApi {
  MaintenanceResult refreshPrices();

  Object refreshCurrency();

  MaintenanceResult updateHistory();

  MaintenanceResult rebuildMonthly();

  Object updateManualAssetPrice(String symbol, double marketPrice);

  record MaintenanceResult(String status, String message, ZonedDateTime refreshedAt) {}
}
