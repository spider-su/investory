package com.smartbox.investory.investment.api.portfolio;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Reads the optional business classification for a brokerage asset symbol. */
public interface BrokerageAssetClassificationReader {
  Optional<BrokerageAssetClassification> findBySymbol(String symbol);

  /**
   * Reads all requested classifications in one source-domain lookup. Missing symbols are absent.
   */
  Map<String, BrokerageAssetClassification> findBySymbols(Collection<String> symbols);
}
