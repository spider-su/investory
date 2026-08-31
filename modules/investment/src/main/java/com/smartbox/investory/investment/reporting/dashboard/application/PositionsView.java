package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.performance.model.OpenPositionValue;
import java.util.List;

public record PositionsView(
    double unrealizedProfit,
    List<OpenPositionValue> openPositionValues,
    OpenPositionValue openPositionValuesTotal) {

  public PositionsView {
    openPositionValues = openPositionValues == null ? List.of() : List.copyOf(openPositionValues);
  }
}
