package com.smartbox.investory.application.dashboard;

import com.smartbox.investory.infrastructure.CurrencyType;
import com.smartbox.investory.services.models.DividendGainer;
import java.util.List;
import java.util.Map;

public record CashFlowView(
    double deposits,
    double withdrawals,
    double cash,
    double realizedProfit,
    Map<CurrencyType, Double> realizedByCurrency,
    double dividends,
    double dividendTax,
    double interest,
    double capitalGainsTax,
    double lossCarryForward,
    List<DividendGainer> dividendGainers) {

  public CashFlowView {
    realizedByCurrency = realizedByCurrency == null ? Map.of() : Map.copyOf(realizedByCurrency);
    dividendGainers = dividendGainers == null ? List.of() : List.copyOf(dividendGainers);
  }
}
