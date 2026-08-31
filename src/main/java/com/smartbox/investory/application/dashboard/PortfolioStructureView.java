package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.infrastructure.CurrencyType;
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
    topHoldings = topHoldings == null ? List.of() : List.copyOf(topHoldings);
    accountCurrencies = accountCurrencies == null ? List.of() : List.copyOf(accountCurrencies);
  }

  public record Holding(String symbol, double value, double weightPct, double unrealized) {}

  public record CurrencyBucket(
      CurrencyType currency, double value, double weightPct, List<String> accounts) {
    public CurrencyBucket {
      accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }
  }
}
