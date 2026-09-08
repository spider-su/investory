package com.smartbox.investory.investment.operations.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.valuation.price.ManualAssetPriceService;
import com.smartbox.investory.investment.valuation.price.MarketDataService;
import com.smartbox.investory.investment.valuation.price.PriceHistoryCoverageService;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class InvestmentMaintenanceApplicationServiceTest {
  private final MarketDataService market = mock();
  private final ManualAssetPriceService manualPrices = mock();
  private final PortfolioProjectionService projections = mock();
  private final PortfolioProjectionRefreshService projectionRefresh = mock();
  private final CurrencyRateUpdaterService currencyRates = mock();
  private final PriceHistoryCoverageService coverage = mock();
  private final ApplicationTime applicationTime = mock();
  private InvestmentMaintenanceApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new InvestmentMaintenanceApplicationService(
            market,
            manualPrices,
            projections,
            projectionRefresh,
            currencyRates,
            coverage,
            applicationTime);
  }

  @Test
  void refreshPricesOnlyUpdatesMarketPrices() {
    var result = service.refreshPrices();

    assertEquals("OK", result.status());
    verify(market).fullPortfolioUpdate();
    verifyNoMoreInteractions(
        market, manualPrices, projections, projectionRefresh, currencyRates, coverage);
  }

  @Test
  void refreshCurrencyMapsUpdaterResultWithoutOtherRefreshes() {
    var expected =
        new CurrencyRateUpdaterService.CurrencyRateRefreshResult(
            LocalDate.of(2026, 9, 8), List.of("USD"), List.of("EUR: unavailable"));
    when(currencyRates.updateCurrencyRates()).thenReturn(expected);

    assertEquals(
        new com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi
            .CurrencyRefreshResult(expected.rateDate(), expected.updated(), expected.failed()),
        service.refreshCurrency());
    verify(currencyRates).updateCurrencyRates();
    verifyNoMoreInteractions(currencyRates);
  }

  @Test
  void updateHistoryPreservesRefreshOrder() {
    service.updateHistory();

    InOrder order = inOrder(market, coverage, projections, projectionRefresh);
    order.verify(market).refreshMarketPricesAndPositions();
    order.verify(coverage).ensurePortfolioCoverage(null);
    order.verify(projections).recalculateAll();
    order
        .verify(projectionRefresh)
        .refreshApplicationViews(
            PortfolioProjectionRefreshService.ApplicationRefreshScope.MARKET_HISTORY);
    order.verify(projections).refreshReconciliationViews();
    verifyNoMoreInteractions(market, coverage, projections, projectionRefresh);
  }

  @Test
  void rebuildMonthlyRefreshesDerivedViewsInOrder() {
    service.rebuildMonthly();

    InOrder order = inOrder(projections, projectionRefresh);
    order.verify(projections).recalculateAll();
    order
        .verify(projectionRefresh)
        .refreshApplicationViews(PortfolioProjectionRefreshService.ApplicationRefreshScope.FULL);
    order.verify(projections).refreshReconciliationViews();
    verifyNoMoreInteractions(projections, projectionRefresh);
  }

  @Test
  void updateManualAssetPriceMapsResult() {
    var updatedAt = ZonedDateTime.of(2026, 9, 8, 12, 0, 0, 0, ZoneId.of("UTC"));
    var expected =
        new ManualAssetPriceService.ManualAssetPrice(
            "ABC",
            BigDecimal.TEN,
            BigDecimal.TEN,
            com.smartbox.investory.shared.currency.CurrencyType.USD,
            "Manual",
            updatedAt);
    when(manualPrices.updatePrice("ABC", BigDecimal.TEN)).thenReturn(expected);

    var actual = service.updateManualAssetPrice("ABC", BigDecimal.TEN);

    assertEquals(expected.symbol(), actual.symbol());
    assertEquals(expected.marketPrice(), actual.marketPrice());
    assertEquals(expected.marketPriceUsd(), actual.marketPriceUsd());
    assertEquals(expected.currency(), actual.currency());
    assertEquals(expected.source(), actual.source());
    assertEquals(expected.updatedAt(), actual.updatedAt());
    verify(manualPrices).updatePrice("ABC", BigDecimal.TEN);
  }

  @Test
  void updateManualAssetPriceMapsInvalidInput() {
    when(manualPrices.updatePrice("ABC", BigDecimal.ZERO))
        .thenThrow(new IllegalArgumentException("Market price must be positive"));

    var error =
        assertThrows(
            com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi
                .InvalidMaintenanceRequest.class,
            () -> service.updateManualAssetPrice("ABC", BigDecimal.ZERO));

    assertEquals("Market price must be positive", error.getMessage());
    verify(manualPrices).updatePrice("ABC", BigDecimal.ZERO);
  }
}
