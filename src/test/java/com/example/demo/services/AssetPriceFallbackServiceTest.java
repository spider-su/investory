package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import com.example.demo.infrastructure.repository.OpenedPosition;
import com.example.demo.infrastructure.repository.OpenedPositionRepository;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.testsupport.portfolio.PortfolioBuilders;
import com.example.demo.testsupport.portfolio.PortfolioTestData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetPriceFallbackServiceTest {

  @Mock private OpenedPositionRepository openedPositionRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private CurrencyRateService currencyRateService;

  @Test
  @SuppressWarnings("unchecked")
  void populateMissingPricesFromOpenPositions_usesWeightedOpenPriceAndUsdConversion() {
    AssetPriceFallbackService service =
        new AssetPriceFallbackService(
            openedPositionRepository, assetRepository, currencyRateService);

    OpenedPosition first = position(10.0, 200.0);
    OpenedPosition second = position(30.0, 220.0);
    Asset asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();

    when(openedPositionRepository.findAll()).thenReturn(List.of(first, second));
    when(assetRepository.findAllBySymbolIn(any())).thenReturn(List.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            eq(215.0), eq(CurrencyType.USD), eq(CurrencyType.PLN), any(LocalDate.class)))
        .thenReturn(53.75);

    service.populateMissingPricesFromOpenPositions();

    ArgumentCaptor<Iterable<Asset>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(assetRepository).saveAll(captor.capture());
    Asset saved = toList(captor.getValue()).getFirst();
    assertEquals("PKO.PL", saved.getSymbol());
    assertEquals(215.0, saved.getMarketPrice(), 0.001);
    assertEquals(53.75, saved.getMarketPriceUsd(), 0.001);
    assertEquals("OpenPositionWeightedAverage", saved.getPriceSource());
  }

  @Test
  void populateMissingPricesFromOpenPositions_doesNotOverwriteExistingPrice() {
    AssetPriceFallbackService service =
        new AssetPriceFallbackService(
            openedPositionRepository, assetRepository, currencyRateService);

    OpenedPosition position =
        PortfolioBuilders.openPosition(PortfolioTestData.SPY)
            .quantity(2.0)
            .price(100.0)
            .build();
    Asset asset =
        PortfolioBuilders.asset(PortfolioTestData.SPY)
            .withLatestPrice(120.0, 120.0, PortfolioTestData.JANUARY_MONTH_END)
            .build();
    asset.setPriceSource("TwelveData");

    when(openedPositionRepository.findAll()).thenReturn(List.of(position));
    when(assetRepository.findAllBySymbolIn(any())).thenReturn(List.of(asset));

    service.populateMissingPricesFromOpenPositions();

    verify(assetRepository, never()).saveAll(any());
    verify(currencyRateService, never()).convertToBaseCurrency(anyDouble(), any(), any(), any());
  }

  private static OpenedPosition position(double volume, double openPrice) {
    return PortfolioBuilders.openPosition(PortfolioTestData.PKO_WA)
        .quantity(volume)
        .price(openPrice)
        .build();
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    java.util.ArrayList<T> list = new java.util.ArrayList<>();
    iterable.forEach(list::add);
    return list;
  }
}
