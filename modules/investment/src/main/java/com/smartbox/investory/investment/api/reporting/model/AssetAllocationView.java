package com.smartbox.investory.investment.api.reporting.model;

import java.math.BigDecimal;
import java.util.List;

public record AssetAllocationView(BigDecimal totalValue, List<Bucket> buckets) {
  public AssetAllocationView(double totalValue, List<Bucket> buckets) {
    this(BigDecimal.valueOf(totalValue), buckets);
  }

  public AssetAllocationView {
    buckets = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(buckets);
  }

  public record Bucket(String name, BigDecimal value, BigDecimal weightPct, List<String> symbols) {
    public Bucket(String name, double value, double weightPct, List<String> symbols) {
      this(name, BigDecimal.valueOf(value), BigDecimal.valueOf(weightPct), symbols);
    }

    public Bucket {
      symbols = com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(symbols);
    }
  }
}
