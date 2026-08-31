package com.smartbox.investory.investment.api.reporting.model;

import java.util.List;

public record PositionsView(
    double unrealizedProfit,
    List<OpenPositionValue> openPositionValues,
    OpenPositionValue openPositionValuesTotal) {

  public PositionsView {
    openPositionValues =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(openPositionValues);
  }
}
