package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.services.models.OpenPositionValue;
import java.util.List;
import java.util.Map;

public record PositionsView(
    double unrealizedProfit,
    Map<CurrencyType, Double> unrealizedByCurrency,
    List<OpenPositionValue> openPositionValues,
    OpenPositionValue openPositionValuesTotal) {

  public PositionsView {
    unrealizedByCurrency =
        unrealizedByCurrency == null ? Map.of() : Map.copyOf(unrealizedByCurrency);
    openPositionValues = openPositionValues == null ? List.of() : List.copyOf(openPositionValues);
  }
}
