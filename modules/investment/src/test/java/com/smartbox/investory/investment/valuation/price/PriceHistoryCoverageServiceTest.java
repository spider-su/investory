package com.smartbox.investory.investment.valuation.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.investment.ledger.position.persistence.PositionEntity;
import com.smartbox.investory.investment.ledger.position.persistence.PositionRepository;
import com.smartbox.investory.investment.performance.InvestmentCalculationCache;
import com.smartbox.investory.investment.port.market.MarketDataProvider;
import com.smartbox.investory.investment.valuation.price.persistence.AssetPriceHistoryRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PriceHistoryCoverageServiceTest {
  private PositionRepository positions;
  private AssetRepository assets;
  private AssetPriceHistoryRepository history;
  private MarketDataProvider market;
  private InvestmentCalculationCache cache;
  private PriceHistoryCoverageService service;

  @BeforeEach
  void setUp() {
    positions = mock(PositionRepository.class);
    assets = mock(AssetRepository.class);
    history = mock(AssetPriceHistoryRepository.class);
    market = mock(MarketDataProvider.class);
    cache = mock(InvestmentCalculationCache.class);
    service =
        new PriceHistoryCoverageService(
            positions,
            assets,
            history,
            market,
            cache,
            Clock.fixed(Instant.parse("2026-02-03T00:00:00Z"), ZoneOffset.UTC),
            "2025-01-01",
            "SPY");
  }

  @Test
  void invalidRequestAndMissingMappingReturnNoSource() {
    assertThat(service.ensureAssetCoverage(null, LocalDate.now(), LocalDate.now()).status())
        .isEqualTo(PriceHistoryCoverageService.CoverageStatus.NO_SOURCE);
    when(assets.findById(5L))
        .thenReturn(Optional.of(AssetEntity.builder().id(5L).ticker(" ").build()));
    var result =
        service.ensureAssetCoverage(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
    assertThat(result.status()).isEqualTo(PriceHistoryCoverageService.CoverageStatus.NO_SOURCE);
    assertThat(result.warnings()).containsExactly("No source mapping");
  }

  @Test
  void boundedExistingHistoryAvoidsProviderCall() {
    AssetEntity asset = asset(5L, "AAPL", "AAPL.US");
    when(assets.findById(5L)).thenReturn(Optional.of(asset));
    var first = row("2026-01-01");
    var last = row("2026-01-03");
    when(history.findHistoricalPricesBySymbolInBefore(List.of("AAPL"), LocalDate.of(2026, 1, 3)))
        .thenReturn(List.of(first, last));

    var result =
        service.ensureAssetCoverage(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

    assertThat(result.status()).isEqualTo(PriceHistoryCoverageService.CoverageStatus.COMPLETE);
    assertThat(result.existingObservations()).isEqualTo(2);
    verify(market, never()).fetchDailyCloses(any(), any(), any());
  }

  @Test
  void fetchedHistoryWritesOnlyMissingDatesAndInvalidatesCache() {
    AssetEntity asset = asset(5L, "AAPL", "AAPL.US");
    when(assets.findById(5L)).thenReturn(Optional.of(asset));
    var existing = row("2026-01-02");
    when(history.findHistoricalPricesBySymbolInBefore(List.of("AAPL"), LocalDate.of(2026, 1, 3)))
        .thenReturn(List.of(existing));
    when(market.externalSymbol("AAPL", "AAPL.US")).thenReturn("AAPL.US");
    NavigableMap<LocalDate, Double> fetched = new TreeMap<>();
    fetched.put(LocalDate.of(2026, 1, 2), 100.0);
    fetched.put(LocalDate.of(2026, 1, 3), 101.25);
    when(market.fetchDailyCloses("AAPL.US", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3)))
        .thenReturn(fetched);

    var result =
        service.ensureAssetCoverage(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

    assertThat(result.fetchedObservations()).isEqualTo(1);
    verify(history)
        .upsertObservedPrice(
            5L,
            LocalDate.of(2026, 1, 3),
            "TWELVE_DATA",
            "AAPL.US",
            "AAPL",
            "TWELVE_DATA_MARKET_CLOSE",
            "USD",
            new BigDecimal("101.25"),
            100,
            "EXACT_LISTING_MARKET_CLOSE");
    verify(cache).invalidate();
  }

  @Test
  void emptyProviderResponseIsReportedWithoutCacheInvalidation() {
    when(assets.findById(5L)).thenReturn(Optional.of(asset(5L, "AAPL", "AAPL.US")));
    when(history.findHistoricalPricesBySymbolInBefore(any(), any())).thenReturn(List.of());
    when(market.externalSymbol("AAPL", "AAPL.US")).thenReturn("AAPL.US");
    when(market.fetchDailyCloses(any(), any(), any())).thenReturn(new TreeMap<>());

    var result =
        service.ensureAssetCoverage(5L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3));

    assertThat(result.status())
        .isEqualTo(PriceHistoryCoverageService.CoverageStatus.PROVIDER_ERROR);
    verify(cache, never()).invalidate();
  }

  @Test
  void portfolioCoverageUsesEarliestOpenDateAndAddsBenchmark() {
    PositionEntity later =
        PositionEntity.builder()
            .assetId(5L)
            .openTime(ZonedDateTime.parse("2026-01-10T00:00:00Z"))
            .build();
    PositionEntity earlier =
        PositionEntity.builder()
            .assetId(5L)
            .openTime(ZonedDateTime.parse("2026-01-05T00:00:00Z"))
            .build();
    when(positions.findOpen())
        .thenReturn(List.of(later, earlier, PositionEntity.builder().build()));
    when(assets.findById(5L)).thenReturn(Optional.empty());
    when(assets.findBySymbol("SPY")).thenReturn(Optional.of(asset(9L, "SPY", "SPY")));
    when(assets.findById(9L)).thenReturn(Optional.empty());

    var result = service.ensurePortfolioCoverage(1L);

    assertThat(result.results()).hasSize(2);
    assertThat(result.results().getFirst().requestedFrom()).isEqualTo(LocalDate.of(2026, 1, 5));
    assertThat(result.results().get(1).requestedFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
  }

  private static AssetEntity asset(Long id, String symbol, String ticker) {
    return AssetEntity.builder()
        .id(id)
        .symbol(symbol)
        .ticker(ticker)
        .currency(CurrencyType.USD)
        .build();
  }

  private static AssetPriceHistoryRepository.HistoricalAssetPriceRow row(String date) {
    var row = mock(AssetPriceHistoryRepository.HistoricalAssetPriceRow.class);
    when(row.getPriceDate()).thenReturn(LocalDate.parse(date));
    return row;
  }
}
