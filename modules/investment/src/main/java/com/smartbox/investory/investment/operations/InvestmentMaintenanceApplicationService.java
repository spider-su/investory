package com.smartbox.investory.investment.operations;

import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.api.operations.ManualAssetPriceView;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.valuation.price.ManualAssetPriceService;
import com.smartbox.investory.investment.valuation.price.MarketDataService;
import com.smartbox.investory.investment.valuation.price.PriceHistoryCoverageService;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Composes investment maintenance actions behind the public API. */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentMaintenanceApplicationService implements InvestmentMaintenanceApi {
  private final MarketDataService market;
  private final ManualAssetPriceService manualPrices;
  private final PortfolioProjectionService projections;
  private final PortfolioProjectionRefreshService projectionRefreshService;
  private final CurrencyRateUpdaterService currencyRates;
  private final PriceHistoryCoverageService coverage;
  private final ApplicationTime applicationTime;

  @Override
  public MaintenanceResult refreshPrices() {
    return command(
        "refresh prices",
        () -> {
          market.fullPortfolioUpdate();
          return result("Open position prices refreshed");
        });
  }

  @Override
  public CurrencyRefreshResult refreshCurrency() {
    return command(
        "refresh currency rates",
        () -> {
          var result = currencyRates.updateCurrencyRates();
          return new CurrencyRefreshResult(result.rateDate(), result.updated(), result.failed());
        });
  }

  @Override
  public MaintenanceResult updateHistory() {
    return command(
        "update market history",
        () -> {
          market.refreshMarketPricesAndPositions();
          coverage.ensurePortfolioCoverage(null);
          projections.recalculateAll();
          projectionRefreshService.refreshApplicationViews(
              PortfolioProjectionRefreshService.ApplicationRefreshScope.MARKET_HISTORY);
          projections.refreshReconciliationViews();
          return result("Market prices refreshed and history rebuilt");
        });
  }

  @Override
  public MaintenanceResult rebuildMonthly() {
    return command(
        "rebuild monthly statistics",
        () -> {
          projections.recalculateAll();
          projectionRefreshService.refreshApplicationViews(
              PortfolioProjectionRefreshService.ApplicationRefreshScope.FULL);
          projections.refreshReconciliationViews();
          return result("AccountEntity stats rebuilt");
        });
  }

  @Override
  public ManualAssetPriceView updateManualAssetPrice(String symbol, BigDecimal marketPrice) {
    final ManualAssetPriceService.ManualAssetPrice price;
    try {
      price =
          command(
              "update manual asset price " + symbol,
              () -> manualPrices.updatePrice(symbol, marketPrice));
    } catch (IllegalArgumentException exception) {
      throw new InvalidMaintenanceRequest(exception.getMessage(), exception);
    }
    return new ManualAssetPriceView(
        price.symbol(),
        price.marketPrice(),
        price.marketPriceUsd(),
        price.currency(),
        price.source(),
        price.updatedAt());
  }

  private <T> T command(String operation, java.util.function.Supplier<T> action) {
    try {
      T value = action.get();
      log.info("Investment operation succeeded: {}", operation);
      return value;
    } catch (RuntimeException exception) {
      log.error("Investment operation failed: {}", operation, exception);
      throw exception;
    }
  }

  private MaintenanceResult result(String message) {
    return new MaintenanceResult(
        "OK", message, applicationTime.now(applicationTime.businessZone()));
  }
}
