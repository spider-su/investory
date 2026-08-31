package com.smartbox.investory.investment.reporting.dashboard.application;

import java.util.List;

public record AssetAllocationView(double totalValue, List<Bucket> buckets) {
  public AssetAllocationView {
    buckets = buckets == null ? List.of() : List.copyOf(buckets);
  }

  public record Bucket(String name, double value, double weightPct, List<String> symbols) {
    public Bucket {
      symbols = symbols == null ? List.of() : List.copyOf(symbols);
    }

    public String cssKey() {
      return switch (name) {
        case "ETF" -> "etf";
        case "Equity" -> "equity";
        case "REIT / real estate" -> "real-estate";
        case "Fixed income" -> "fixed-income";
        case "Commodity / metal" -> "commodity";
        case "Cash" -> "cash";
        default -> "other";
      };
    }
  }
}
