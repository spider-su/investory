package com.smartbox.investory.services.portfolio.read.internal;

import com.smartbox.investory.infrastructure.repository.AssetRepository;
import com.smartbox.investory.services.portfolio.read.BrokerageAssetClassification;
import com.smartbox.investory.services.portfolio.read.BrokerageAssetClassificationReader;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repository-backed implementation of the brokerage asset classification boundary. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BrokerageAssetClassificationReadService implements BrokerageAssetClassificationReader {
  private final AssetRepository assetRepository;

  @Override
  public Optional<BrokerageAssetClassification> findBySymbol(String symbol) {
    return assetRepository
        .findBySymbol(symbol)
        .map(asset -> new BrokerageAssetClassification(asset.getSymbol(), asset.getAssetType()));
  }
}
