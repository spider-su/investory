package com.smartbox.investory.investment.valuation.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.projection.StatisticsRefreshService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.ManualAssetPriceService.ManualAssetPrice;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import com.smartbox.investory.testsupport.time.MutableApplicationTime;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("Manual Asset Price Service")
class ManualAssetPriceServiceTest {

  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private CurrencyRateService currencyRateService;
  @Mock private MarketDataService marketDataService;
  @Mock private StatisticsRefreshService statisticsRefreshService;

  private ManualAssetPriceService service;

  @BeforeEach
  void setUp() {
    service =
        new ManualAssetPriceService(
            assetRepository,
            assetPriceHistoryRepository,
            currencyRateService,
            marketDataService,
            statisticsRefreshService,
            MutableApplicationTime.fixed(
                Instant.parse("2026-09-05T08:00:00Z"), ZoneId.of("Europe/Warsaw")));
  }

  @DisplayName("update Price Saves Manual Price And Refreshes Derived State")
  @Test
  void updatePriceSavesManualPriceAndRefreshesDerivedState() {
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
    when(assetRepository.findBySymbol("PKO.PL")).thenReturn(Optional.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            eq(BigDecimal.valueOf(123.45)),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenReturn(BigDecimal.valueOf(30.0));

    ManualAssetPrice result = service.updatePrice("PKO.PL", BigDecimal.valueOf(123.45));

    assertEquals("PKO.PL", result.symbol());
    assertEquals(0, result.marketPrice().compareTo(BigDecimal.valueOf(123.45)));
    assertEquals(0, result.marketPriceUsd().compareTo(BigDecimal.valueOf(30.0)));
    assertEquals("Manual", result.source());
    assertEquals(0, asset.getMarketPrice().compareTo(BigDecimal.valueOf(123.45)));
    assertEquals(0, asset.getMarketPriceUsd().compareTo(BigDecimal.valueOf(30.0)));
    assertEquals("Manual", asset.getPriceSource());

    verify(assetRepository).save(asset);
    verify(assetPriceHistoryRepository)
        .upsertObservedPrice(
            eq(asset.getId()),
            any(LocalDate.class),
            eq("MANUAL"),
            eq("PKO.PL"),
            eq("PKO.PL"),
            eq("MANUAL"),
            eq("PLN"),
            eq(BigDecimal.valueOf(123.45)),
            eq(100),
            eq("MANUAL"));
    verify(marketDataService).syncIbkrPositions();
    verify(statisticsRefreshService).refreshAll();
  }

  @DisplayName("update Price Rejects Non Positive Price")
  @Test
  void updatePriceRejectsNonPositivePrice() {
    assertThrows(
        IllegalArgumentException.class, () -> service.updatePrice("CDR.PL", BigDecimal.ZERO));

    verifyNoInteractions(assetRepository, marketDataService, statisticsRefreshService);
  }

  @DisplayName("update Price Rejects Blank Symbol")
  @Test
  void updatePriceRejectsBlankSymbol() {
    assertThrows(IllegalArgumentException.class, () -> service.updatePrice(" ", BigDecimal.TEN));

    verifyNoInteractions(assetRepository, marketDataService, statisticsRefreshService);
  }

  @DisplayName("update Price Rejects Missing Asset Without Refreshing Derived State")
  @Test
  void updatePriceRejectsMissingAssetWithoutRefreshingDerivedState() {
    when(assetRepository.findBySymbol("MISSING.US")).thenReturn(Optional.empty());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.updatePrice("MISSING.US", BigDecimal.TEN));

    assertEquals("AssetEntity not found: MISSING.US", exception.getMessage());
    verify(assetRepository).findBySymbol("MISSING.US");
    verifyNoInteractions(currencyRateService, marketDataService, statisticsRefreshService);
  }

  @DisplayName("update Price Rejects Excluded Asset Without Changing History")
  @Test
  void updatePriceRejectsExcludedAssetWithoutChangingHistory() {
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
    asset.setExcludeFromImport(true);
    when(assetRepository.findBySymbol("PKO.PL")).thenReturn(Optional.of(asset));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> service.updatePrice("PKO.PL", BigDecimal.TEN));

    assertEquals(
        "AssetEntity is excluded from Investory calculations: PKO.PL", exception.getMessage());
    verifyNoInteractions(
        currencyRateService,
        assetPriceHistoryRepository,
        marketDataService,
        statisticsRefreshService);
  }

  @DisplayName("update Price Refreshes Projections Only After The Price Transaction Commits")
  @Test
  void updatePriceRefreshesProjectionsOnlyAfterThePriceTransactionCommits() {
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
    when(assetRepository.findBySymbol("PKO.PL")).thenReturn(Optional.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            eq(BigDecimal.valueOf(123.45)),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenReturn(BigDecimal.valueOf(30.0));

    TransactionSynchronizationManager.initSynchronization();
    try {
      service.updatePrice("PKO.PL", BigDecimal.valueOf(123.45));

      verifyNoInteractions(statisticsRefreshService);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(synchronization -> synchronization.afterCommit());
      verify(statisticsRefreshService).refreshAllAfterCommittedMutation();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
