package com.smartbox.investory.investment.market.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.persistence.AssetEntity;
import com.smartbox.investory.investment.infrastructure.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateService;
import com.smartbox.investory.investment.market.price.ManualAssetPriceService.ManualAssetPrice;
import com.smartbox.investory.investment.reporting.StatisticsRefreshService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ManualAssetPriceServiceTest {

  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private CurrencyRateService currencyRateService;
  @Mock private MarketService marketService;
  @Mock private StatisticsRefreshService statisticsRefreshService;

  private ManualAssetPriceService service;

  @BeforeEach
  void setUp() {
    service =
        new ManualAssetPriceService(
            assetRepository,
            assetPriceHistoryRepository,
            currencyRateService,
            marketService,
            statisticsRefreshService);
  }

  @Test
  void updatePriceSavesManualPriceAndRefreshesDerivedState() {
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
    when(assetRepository.findBySymbol("PKO.PL")).thenReturn(Optional.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            eq(123.45), eq(CurrencyType.USD), eq(CurrencyType.PLN), any(LocalDate.class)))
        .thenReturn(30.0);

    ManualAssetPrice result = service.updatePrice("PKO.PL", 123.45);

    assertEquals("PKO.PL", result.symbol());
    assertEquals(123.45, result.marketPrice(), 0.000001);
    assertEquals(30.0, result.marketPriceUsd(), 0.000001);
    assertEquals("Manual", result.source());
    assertEquals(123.45, asset.getMarketPrice(), 0.000001);
    assertEquals(30.0, asset.getMarketPriceUsd(), 0.000001);
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
    verify(marketService).syncIbkrPositions();
    verify(statisticsRefreshService).refreshAll();
  }

  @Test
  void updatePriceRejectsNonPositivePrice() {
    assertThrows(IllegalArgumentException.class, () -> service.updatePrice("CDR.PL", 0.0));

    verifyNoInteractions(assetRepository, marketService, statisticsRefreshService);
  }

  @Test
  void updatePriceRejectsBlankSymbol() {
    assertThrows(IllegalArgumentException.class, () -> service.updatePrice(" ", 10.0));

    verifyNoInteractions(assetRepository, marketService, statisticsRefreshService);
  }

  @Test
  void updatePriceRejectsMissingAssetWithoutRefreshingDerivedState() {
    when(assetRepository.findBySymbol("MISSING.US")).thenReturn(Optional.empty());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> service.updatePrice("MISSING.US", 10.0));

    assertEquals("AssetEntity not found: MISSING.US", exception.getMessage());
    verify(assetRepository).findBySymbol("MISSING.US");
    verifyNoInteractions(currencyRateService, marketService, statisticsRefreshService);
  }

  @Test
  void updatePriceRejectsExcludedAssetWithoutChangingHistory() {
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
    asset.setExcludeFromImport(true);
    when(assetRepository.findBySymbol("PKO.PL")).thenReturn(Optional.of(asset));

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> service.updatePrice("PKO.PL", 10.0));

    assertEquals("AssetEntity is excluded from Investory calculations: PKO.PL", exception.getMessage());
    verifyNoInteractions(
        currencyRateService, assetPriceHistoryRepository, marketService, statisticsRefreshService);
  }

  @Test
  void updatePriceRefreshesProjectionsOnlyAfterThePriceTransactionCommits() {
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
    when(assetRepository.findBySymbol("PKO.PL")).thenReturn(Optional.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            eq(123.45), eq(CurrencyType.USD), eq(CurrencyType.PLN), any(LocalDate.class)))
        .thenReturn(30.0);

    TransactionSynchronizationManager.initSynchronization();
    try {
      service.updatePrice("PKO.PL", 123.45);

      verifyNoInteractions(statisticsRefreshService);
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(synchronization -> synchronization.afterCommit());
      verify(statisticsRefreshService).refreshAllAfterCommittedMutation();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
