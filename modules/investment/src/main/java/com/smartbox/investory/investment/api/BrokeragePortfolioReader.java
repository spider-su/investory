package com.smartbox.investory.investment.api;

/** Public read boundary over the current brokerage reporting aggregation. */
public interface BrokeragePortfolioReader {
  SharedBrokeragePortfolioSnapshot currentSharedSnapshot();
}
