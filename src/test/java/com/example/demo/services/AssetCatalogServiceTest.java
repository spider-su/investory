package com.example.demo.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.CurrencyType;
import com.example.demo.infrastructure.repository.Asset;
import com.example.demo.infrastructure.repository.AssetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetCatalogServiceTest {

  @Mock private AssetRepository assetRepository;

  @Test
  void ensureAssetsExistCreatesRequestedSymbolWhenTickerDoesNotExist() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());
    when(assetRepository.findAllByTickerIn(anyCollection())).thenReturn(List.of());

    service.ensureAssetsExist(List.of(service.seedForSymbol("ASML.NL", CurrencyType.EUR)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<Asset>> captor =
        (ArgumentCaptor<Iterable<Asset>>)
            (ArgumentCaptor<?>) ArgumentCaptor.forClass(Iterable.class);
    verify(assetRepository).saveAll(captor.capture());
    Asset asset = captor.getValue().iterator().next();
    assertEquals("ASML.NL", asset.getSymbol());
    assertEquals("ASML", asset.getTicker());
    assertEquals(CurrencyType.EUR, asset.getCurrency());
  }

  @Test
  void ensureAssetsExistSkipsRequestedSymbolWhenTickerAlreadyExists() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllBySymbolIn(anyCollection())).thenReturn(List.of());
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("TSLA.US", "TSLA")));

    service.ensureAssetsExist(List.of(service.seedForSymbol("TSLA.DE", CurrencyType.EUR)));

    verify(assetRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void normalizeSymbolsForStorageLoadsExistingTickerAssetsOnce() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByTickerIn(anyCollection()))
        .thenReturn(List.of(asset("TSLA.US", "TSLA")));

    var normalized = service.normalizeSymbolsForStorage(List.of("TSLA.DE", "TSLA.DE", "AAPL.US"));

    assertEquals("TSLA.US", normalized.get("TSLA.DE"));
    assertEquals("AAPL.US", normalized.get("AAPL.US"));
    verify(assetRepository, times(1)).findAllByTickerIn(anyCollection());
  }

  @Test
  void mapIbkrSymbolToCanonicalUsesExistingIbkrMapping() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbrkIgnoreCase("IUVL"))
        .thenReturn(List.of(asset("IUVL.UK", "IUVL")));

    assertEquals("IUVL.UK", service.mapIbkrSymbolToCanonical("IUVL"));
  }

  @Test
  void mapIbkrSymbolToCanonicalAddsUsSuffixForUnknownBareIbkrSymbol() {
    AssetCatalogService service = new AssetCatalogService(assetRepository);
    when(assetRepository.findAllByIbrkIgnoreCase("PG")).thenReturn(List.of());

    assertEquals("PG.US", service.mapIbkrSymbolToCanonical("PG"));
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
