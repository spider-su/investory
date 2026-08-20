package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.BrokerageAssetClassification;
import com.smartbox.investory.investment.api.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.infrastructure.persistence.AssetRepository;
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
