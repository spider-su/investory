package com.smartbox.investory.investment.accounting;

import com.smartbox.investory.investment.api.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.market.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.market.price.ManualAssetPriceService;
import com.smartbox.investory.investment.market.price.MarketService;
import com.smartbox.investory.investment.market.price.PriceHistoryCoverageService;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Composes investment maintenance actions behind the public API. */
@Service
@RequiredArgsConstructor
public class InvestmentMaintenanceApplicationService implements InvestmentMaintenanceApi {
  private final MarketService market;
  private final ManualAssetPriceService manualPrices;
  private final PortfolioProjectionService projections;
  private final CurrencyRateUpdaterService currencyRates;

  @Autowired(required = false)
  private PriceHistoryCoverageService coverage;

  @Override
  public MaintenanceResult refreshPrices() {
    market.fullPortfolioUpdate();
    return result("Open position prices refreshed");
  }

  @Override
  public Object refreshCurrency() {
    return currencyRates.updateCurrencyRates();
  }

  @Override
  public MaintenanceResult updateHistory() {
    market.refreshMarketPricesAndPositions();
    if (coverage != null) coverage.ensurePortfolioCoverage(null);
    projections.recalculateAll();
    projections.refreshReconciliationViews();
    return result("Market prices refreshed and history rebuilt");
  }

  @Override
  public MaintenanceResult rebuildMonthly() {
    projections.recalculateAll();
    projections.refreshReconciliationViews();
    return result("AccountEntity stats rebuilt");
  }

  @Override
  public Object updateManualAssetPrice(String symbol, double marketPrice) {
    return manualPrices.updatePrice(symbol, marketPrice);
  }

  private MaintenanceResult result(String message) {
    return new MaintenanceResult("OK", message, ZonedDateTime.now());
  }
}
