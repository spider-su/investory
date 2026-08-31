package com.smartbox.investory.investment.operations;

import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.valuation.price.ManualAssetPriceService;
import com.smartbox.investory.investment.valuation.price.MarketService;
import com.smartbox.investory.investment.valuation.price.PriceHistoryCoverageService;
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
  public CurrencyRefreshResult refreshCurrency() {
    var result = currencyRates.updateCurrencyRates();
    return new CurrencyRefreshResult(result.rateDate(), result.updated(), result.failed());
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
