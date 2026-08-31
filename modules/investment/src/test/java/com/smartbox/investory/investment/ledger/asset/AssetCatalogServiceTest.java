package com.smartbox.investory.investment.ledger.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Asset Catalog Service")
class AssetCatalogServiceTest {

  @Mock private AssetRepository assetRepository;

  @DisplayName("ensure Assets Exist Accepts Existing Exact Symbol")
  @Test
  void ensureAssetsExistAcceptsExistingExactSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection()))
        .thenReturn(List.of(asset("ASML.NL", "ASML")));

    service.ensureAssetsExist(List.of(service.seedForSymbol("ASML.NL", CurrencyType.EUR)));

    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @DisplayName("ensure Assets Exist Rejects Unknown Qualified Symbol")
  @Test
  void ensureAssetsExistRejectsUnknownQualifiedSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.ensureAssetsExist(List.of(service.seedForSymbol("TSLA.DE", CurrencyType.EUR))));

    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @DisplayName("normalize Symbols For Storage Rejects Unknown Qualified Symbol")
  @Test
  void normalizeSymbolsForStorageRejectsUnknownQualifiedSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());

    assertThrows(
        IllegalArgumentException.class,
        () -> service.normalizeSymbolsForStorage(List.of("TSLA.DE")));

    verify(assetRepository, never()).findAllByTickerIn(anyCollection());
  }

  @DisplayName("normalize Symbols For Storage Resolves Unique Bare Ticker")
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

  @DisplayName("normalize Symbols For Storage Rejects Ambiguous Bare Ticker")
  @Test
  void normalizeSymbolsForStorageRejectsAmbiguousBareTicker() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("TSLA.US", "TSLA"), asset("TSLA.DE", "TSLA")));

    assertThrows(
        IllegalArgumentException.class, () -> service.normalizeSymbolsForStorage(List.of("TSLA")));
  }

  @DisplayName("map Ibkr Symbol To Canonical Uses Existing Ibkr Mapping")
  @Test
  void mapIbkrSymbolToCanonicalUsesExistingIbkrMapping() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("IUVL"))
        .thenReturn(List.of(asset("IUVL.UK", "IUVL")));

    assertEquals("IUVL.UK", service.mapIbkrSymbolToCanonical("IUVL"));
  }

  @DisplayName("map Ibkr Symbol To Canonical Uses Exact Canonical Symbol")
  @Test
  void mapIbkrSymbolToCanonicalUsesExactCanonicalSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    AssetEntity pg = asset("PG.US", "PG");
    when(assetRepository.findAllByIbkrIgnoreCase("PG.US")).thenReturn(List.of());
    when(assetRepository.findBySymbol("PG.US")).thenReturn(Optional.of(pg));

    assertEquals("PG.US", service.mapIbkrSymbolToCanonical("PG.US"));
  }

  @DisplayName("map Ibkr Symbol To Canonical Uses Unique Existing Ticker")
  @Test
  void mapIbkrSymbolToCanonicalUsesUniqueExistingTicker() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("PG")).thenReturn(List.of());
    when(assetRepository.findBySymbol("PG")).thenReturn(Optional.empty());
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("PG.US", "PG")));

    assertEquals("PG.US", service.mapIbkrSymbolToCanonical("PG"));
  }

  @DisplayName("map Ibkr Symbol To Canonical Rejects Unknown Symbol")
  @Test
  void mapIbkrSymbolToCanonicalRejectsUnknownSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("PG")).thenReturn(List.of());
    when(assetRepository.findBySymbol("PG")).thenReturn(Optional.empty());
    when(assetRepository.findAllByTickerIn(anyCollection())).thenReturn(List.of());

    assertThrows(IllegalArgumentException.class, () -> service.mapIbkrSymbolToCanonical("PG"));
  }

  @DisplayName("map Ibkr Symbol To Canonical Rejects Ambiguous Broker Mapping")
  @Test
  void mapIbkrSymbolToCanonicalRejectsAmbiguousBrokerMapping() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbkrIgnoreCase("ABC"))
        .thenReturn(List.of(asset("ABC.US", "ABC"), asset("ABC.UK", "ABC")));

    assertThrows(IllegalArgumentException.class, () -> service.mapIbkrSymbolToCanonical("ABC"));
  }

  @DisplayName(
      "seed For Symbol Infers Asset Currency From Listing Instead Of Account Currency Hint")
  @Test
  void seedForSymbolInfersAssetCurrencyFromListingInsteadOfAccountCurrencyHint() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);

    assertEquals(CurrencyType.USD, service.seedForSymbol("PG.US", CurrencyType.PLN).currency());
    assertEquals(CurrencyType.EUR, service.seedForSymbol("VWCE.DE", CurrencyType.PLN).currency());
    assertEquals(CurrencyType.PLN, service.seedForSymbol("CDR.PL", CurrencyType.USD).currency());
  }

  private static AssetEntity asset(String symbol, String ticker) {
    return AssetEntity.builder()
        .symbol(symbol)
        .ticker(ticker)
        .currency(CurrencyType.USD)
        .active(true)
        .build();
  }
}
