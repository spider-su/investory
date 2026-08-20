package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.accounting.model.OpenPositionValue;
import com.smartbox.investory.shared.currency.CurrencyType;
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
