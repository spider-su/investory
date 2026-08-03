package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetPriceHistoryRepository;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.services.ManualAssetPriceService.ManualAssetPrice;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    Asset asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();
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
    verify(marketService).syncStocks();
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

    assertEquals("Asset not found: MISSING.US", exception.getMessage());
    verify(assetRepository).findBySymbol("MISSING.US");
    verifyNoInteractions(currencyRateService, marketService, statisticsRefreshService);
  }
}
