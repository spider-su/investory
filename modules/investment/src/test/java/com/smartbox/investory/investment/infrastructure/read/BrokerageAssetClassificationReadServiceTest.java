package com.smartbox.investory.investment.infrastructure.read;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetType;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("Brokerage Asset Classification Read Service")
class BrokerageAssetClassificationReadServiceTest {
  @DisplayName("exposes Only Existing Asset Symbol And Classification")
  @Test
  void exposesOnlyExistingAssetSymbolAndClassification() {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    AssetEntity asset = new AssetEntity();
    asset.setSymbol("ETF-PL");
    asset.setAssetType("ETF");
    when(assets.findBySymbol("ETF-PL")).thenReturn(java.util.Optional.of(asset));

    var result = new BrokerageAssetClassificationReadService(assets).findBySymbol("ETF-PL");

    assertEquals("ETF-PL", result.orElseThrow().symbol());
    assertEquals(BrokerageAssetType.ETF, result.orElseThrow().assetType());
  }

  @DisplayName("preserves Missing Asset Behavior")
  @Test
  void preservesMissingAssetBehavior() {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    when(assets.findBySymbol("UNKNOWN")).thenReturn(java.util.Optional.empty());

    assertTrue(
        new BrokerageAssetClassificationReadService(assets).findBySymbol("UNKNOWN").isEmpty());
  }

  @DisplayName("maps Unknown Persisted Classification To Other")
  @Test
  void mapsUnknownPersistedClassificationToOther() {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    AssetEntity asset = new AssetEntity();
    asset.setSymbol("LEGACY");
    asset.setAssetType("legacy-stock");
    when(assets.findBySymbol("LEGACY")).thenReturn(java.util.Optional.of(asset));

    var result = new BrokerageAssetClassificationReadService(assets).findBySymbol("LEGACY");

    assertEquals(BrokerageAssetType.OTHER, result.orElseThrow().assetType());
  }

  @Test
  void readsBatchClassificationsInOneRepositoryCall() {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    AssetEntity asset = new AssetEntity();
    asset.setSymbol("ETF-PL");
    asset.setAssetType("ETF");
    when(assets.findAllBySymbolIn(java.util.List.of("ETF-PL")))
        .thenReturn(java.util.List.of(asset));

    var result =
        new BrokerageAssetClassificationReadService(assets)
            .findBySymbols(java.util.List.of("ETF-PL"));

    assertEquals(
        Map.of(
            "ETF-PL",
            new com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification(
                "ETF-PL", BrokerageAssetType.ETF)),
        result);
  }
}
