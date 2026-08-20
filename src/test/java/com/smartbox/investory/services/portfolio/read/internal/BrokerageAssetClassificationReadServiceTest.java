package com.smartbox.investory.services.portfolio.read.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.smartbox.investory.infrastructure.repository.Asset;
import com.smartbox.investory.infrastructure.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BrokerageAssetClassificationReadServiceTest {
  @Test
  void exposesOnlyExistingAssetSymbolAndClassification() {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    Asset asset = new Asset();
    asset.setSymbol("ETF-PL");
    asset.setAssetType("ETF");
    when(assets.findBySymbol("ETF-PL")).thenReturn(java.util.Optional.of(asset));

    var result = new BrokerageAssetClassificationReadService(assets).findBySymbol("ETF-PL");

    assertEquals("ETF-PL", result.orElseThrow().symbol());
    assertEquals("ETF", result.orElseThrow().assetType());
  }

  @Test
  void preservesMissingAssetBehavior() {
    AssetRepository assets = Mockito.mock(AssetRepository.class);
    when(assets.findBySymbol("UNKNOWN")).thenReturn(java.util.Optional.empty());

    assertTrue(new BrokerageAssetClassificationReadService(assets).findBySymbol("UNKNOWN").isEmpty());
  }
}
