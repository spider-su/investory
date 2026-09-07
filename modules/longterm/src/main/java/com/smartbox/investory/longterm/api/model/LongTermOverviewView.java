package com.smartbox.investory.longterm.api.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One calculated Long-Term overview read model for the Web adapter. */
public record LongTermOverviewView(
    CurrencyType currency,
    BigDecimal totalValue,
    BigDecimal investmentValue,
    BigDecimal personalAssetValue,
    AnnualEconomicsView economics,
    List<AssetGroupView> groups,
    String largestGroup,
    BigDecimal largestGroupShare) {
  public LongTermOverviewView {
    groups = List.copyOf(groups == null ? List.of() : groups);
  }

  /** Allocation percentages prepared by Long-Term for presentation consumers. */
  public Map<String, BigDecimal> groupShares() {
    Map<String, BigDecimal> shares = new LinkedHashMap<>();
    for (var group : groups) {
      shares.put(
          group.key(),
          totalValue.signum() == 0
              ? BigDecimal.ZERO
              : group
                  .totalValue()
                  .multiply(BigDecimal.valueOf(100))
                  .divide(totalValue, 2, RoundingMode.HALF_UP));
    }
    return Map.copyOf(shares);
  }
}
