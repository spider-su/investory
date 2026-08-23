package com.smartbox.investory.investment.api;

import java.util.Optional;

/** Reads the optional business classification for a brokerage asset symbol. */
public interface BrokerageAssetClassificationReader {
  Optional<BrokerageAssetClassification> findBySymbol(String symbol);
}
