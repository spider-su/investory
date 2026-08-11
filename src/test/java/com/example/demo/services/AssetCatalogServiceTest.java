package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetCatalogServiceTest {

  @Mock private AssetRepository assetRepository;

  @Test
  void ensureAssetsExistAcceptsExistingExactSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection()))
        .thenReturn(List.of(asset("ASML.NL", "ASML")));

    service.ensureAssetsExist(List.of(service.seedForSymbol("ASML.NL", CurrencyType.EUR)));

    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void ensureAssetsExistRejectsUnknownQualifiedSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.ensureAssetsExist(List.of(service.seedForSymbol("TSLA.DE", CurrencyType.EUR))));

    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void normalizeSymbolsForStorageRejectsUnknownQualifiedSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.normalizeSymbolsForStorage(List.of("TSLA.DE")));

    verify(assetRepository, never()).findAllByTickerIn(anyCollection());
  }

  @Test
  void normalizeSymbolsForStorageResolvesUniqueBareTicker() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("TSLA.US", "TSLA")));

    var normalized = service.normalizeSymbolsForStorage(List.of("TSLA", "TSLA"));

    assertEquals("TSLA.US", normalized.get("TSLA"));
    verify(assetRepository, times(1)).findAllByTickerIn(anyCollection());
  }

  @Test
  void normalizeSymbolsForStorageRejectsAmbiguousBareTicker() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("TSLA.US", "TSLA"), asset("TSLA.DE", "TSLA")));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.normalizeSymbolsForStorage(List.of("TSLA")));
  }

  @Test
  void mapIbkrSymbolToCanonicalUsesExistingIbkrMapping() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("IUVL"))
        .thenReturn(List.of(asset("IUVL.UK", "IUVL")));

    assertEquals("IUVL.UK", service.mapIbkrSymbolToCanonical("IUVL"));
  }

  @Test
  void mapIbkrSymbolToCanonicalUsesExactCanonicalSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    Asset pg = asset("PG.US", "PG");
    when(assetRepository.findAllByIbkrIgnoreCase("PG.US")).thenReturn(List.of());
    when(assetRepository.findBySymbol("PG.US")).thenReturn(Optional.of(pg));

    assertEquals("PG.US", service.mapIbkrSymbolToCanonical("PG.US"));
  }

  @Test
  void mapIbkrSymbolToCanonicalUsesUniqueExistingTicker() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("PG")).thenReturn(List.of());
    when(assetRepository.findBySymbol("PG")).thenReturn(Optional.empty());
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("PG.US", "PG")));

    assertEquals("PG.US", service.mapIbkrSymbolToCanonical("PG"));
  }

  @Test
  void mapIbkrSymbolToCanonicalRejectsUnknownSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("PG")).thenReturn(List.of());
    when(assetRepository.findBySymbol("PG")).thenReturn(Optional.empty());
    when(assetRepository.findAllByTickerIn(anyCollection())).thenReturn(List.of());

    assertThrows(
        IllegalArgumentException.class, () -> service.mapIbkrSymbolToCanonical("PG"));
  }

  @Test
  void mapIbkrSymbolToCanonicalRejectsAmbiguousBrokerMapping() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("ABC"))
        .thenReturn(List.of(asset("ABC.US", "ABC"), asset("ABC.UK", "ABC")));

    assertThrows(
        IllegalArgumentException.class, () -> service.mapIbkrSymbolToCanonical("ABC"));
  }

  @Test
  void seedForSymbolInfersAssetCurrencyFromListingInsteadOfAccountCurrencyHint() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);

    assertEquals(CurrencyType.USD, service.seedForSymbol("PG.US", CurrencyType.PLN).currency());
    assertEquals(CurrencyType.EUR, service.seedForSymbol("VWCE.DE", CurrencyType.PLN).currency());
    assertEquals(CurrencyType.PLN, service.seedForSymbol("CDR.PL", CurrencyType.USD).currency());
  }

  private static Asset asset(String symbol, String ticker) {
    return Asset.builder()
        .symbol(symbol)
        .ticker(ticker)
        .currency(CurrencyType.USD)
        .active(true)
        .build();
  }
}
