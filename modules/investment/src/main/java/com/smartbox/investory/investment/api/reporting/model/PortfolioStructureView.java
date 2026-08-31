package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.util.List;

public record PortfolioStructureView(
    double cash,
    double cashWeightPct,
    Holding largestHolding,
    double topFiveWeightPct,
    double topTenWeightPct,
    List<Holding> topHoldings,
    List<CurrencyBucket> accountCurrencies,
    AssetAllocationView assetAllocation) {

  public PortfolioStructureView {
    topHoldings =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(topHoldings);
    accountCurrencies =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(accountCurrencies);
  }

  public record Holding(String symbol, double value, double weightPct, double unrealized) {}

  public record CurrencyBucket(
      CurrencyType currency, double value, double weightPct, List<String> accounts) {
    public CurrencyBucket {
      accounts = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(accounts);
    }
  }
}
