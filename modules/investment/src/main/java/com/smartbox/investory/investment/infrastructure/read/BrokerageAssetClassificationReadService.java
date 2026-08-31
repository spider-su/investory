package com.smartbox.investory.investment.infrastructure.read;

import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassification;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetClassificationReader;
import com.smartbox.investory.investment.api.portfolio.BrokerageAssetType;
import com.smartbox.investory.investment.ledger.asset.persistence.AssetRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repository-backed implementation of the brokerage asset classification boundary. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class BrokerageAssetClassificationReadService implements BrokerageAssetClassificationReader {
  private final AssetRepository assetRepository;

  @Override
  public Optional<BrokerageAssetClassification> findBySymbol(String symbol) {
    return assetRepository.findBySymbol(symbol).map(this::toClassification);
  }

  @Override
  public Map<String, BrokerageAssetClassification> findBySymbols(Collection<String> symbols) {
    if (symbols == null || symbols.isEmpty()) return Map.of();
    return assetRepository.findAllBySymbolIn(symbols).stream()
        .map(this::toClassification)
        .collect(Collectors.toMap(BrokerageAssetClassification::symbol, Function.identity()));
  }

  private BrokerageAssetClassification toClassification(
      com.smartbox.investory.investment.ledger.asset.persistence.AssetEntity asset) {
    if (!BrokerageAssetType.isKnown(asset.getAssetType())) {
      log.warn(
          "Unknown brokerage asset classification '{}' for symbol '{}' mapped to OTHER",
          asset.getAssetType(),
          asset.getSymbol());
    }
    return new BrokerageAssetClassification(
        asset.getSymbol(), BrokerageAssetType.from(asset.getAssetType()));
  }
}
