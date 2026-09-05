package com.smartbox.investory.investment.valuation.price;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioBuilders;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData;
import com.smartbox.investory.testsupport.time.MutableApplicationTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Asset Price Fallback Service")
class AssetPriceFallbackServiceTest {

  private static final MutableApplicationTime TIME =
      MutableApplicationTime.fixed(
          Instant.parse("2026-09-05T08:00:00Z"), ZoneId.of("Europe/Warsaw"));

  private static void assertEquals(double expected, java.math.BigDecimal actual, double delta) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual.doubleValue(), delta);
  }

  private static void assertEquals(double expected, double actual, double delta) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual, delta);
  }

  private static void assertEquals(Object expected, Object actual) {
    org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
  }

  @Mock private PositionRepository openedPositionRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private AssetPriceHistoryRepository assetPriceHistoryRepository;
  @Mock private CurrencyRateService currencyRateService;

  @DisplayName(
      "populate Missing Prices From Open Positions uses Weighted Open Price And Usd Conversion")
  @Test
  @SuppressWarnings("unchecked")
  void populateMissingPricesFromOpenPositions_usesWeightedOpenPriceAndUsdConversion() {
    AssetPriceFallbackService service =
        new AssetPriceFallbackService(
            openedPositionRepository,
            assetRepository,
            assetPriceHistoryRepository,
            currencyRateService,
            TIME);

    PositionEntity first = position(10.0, 200.0);
    PositionEntity second = position(30.0, 220.0);
    AssetEntity asset = PortfolioBuilders.asset(PortfolioTestData.PKO_WA).build();

    when(openedPositionRepository.findOpen()).thenReturn(List.of(first, second));
    when(assetRepository.findAllBySymbolIn(any())).thenReturn(List.of(asset));
    when(currencyRateService.convertToBaseCurrency(
            any(java.math.BigDecimal.class),
            eq(CurrencyType.USD),
            eq(CurrencyType.PLN),
            any(LocalDate.class)))
        .thenReturn(new java.math.BigDecimal("53.75"));

    service.populateMissingPricesFromOpenPositions();

    ArgumentCaptor<Iterable<AssetEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
    verify(assetRepository).saveAll(captor.capture());
    AssetEntity saved = toList(captor.getValue()).getFirst();
    assertEquals("PKO.PL", saved.getSymbol());
    assertEquals(215.0, saved.getMarketPrice(), 0.001);
    assertEquals(53.75, saved.getMarketPriceUsd(), 0.001);
    assertEquals("OpenPositionWeightedAverage", saved.getPriceSource());
  }

  @DisplayName("populate Missing Prices From Open Positions does Not Overwrite Existing Price")
  @Test
  void populateMissingPricesFromOpenPositions_doesNotOverwriteExistingPrice() {
    AssetPriceFallbackService service =
        new AssetPriceFallbackService(
            openedPositionRepository,
            assetRepository,
            assetPriceHistoryRepository,
            currencyRateService,
            TIME);

    PositionEntity position =
        PortfolioBuilders.openPosition(PortfolioTestData.SPY).quantity(2.0).price(100.0).build();
    AssetEntity asset =
        PortfolioBuilders.asset(PortfolioTestData.SPY)
            .withLatestPrice(120.0, 120.0, PortfolioTestData.JANUARY_MONTH_END)
            .build();
    asset.setPriceSource("TwelveData");

    when(openedPositionRepository.findOpen()).thenReturn(List.of(position));
    when(assetRepository.findAllBySymbolIn(any())).thenReturn(List.of(asset));

    service.populateMissingPricesFromOpenPositions();

    verify(assetRepository, never()).saveAll(any());
    verify(currencyRateService, never()).convertToBaseCurrency(anyDouble(), any(), any(), any());
  }

  private static PositionEntity position(double volume, double openPrice) {
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
