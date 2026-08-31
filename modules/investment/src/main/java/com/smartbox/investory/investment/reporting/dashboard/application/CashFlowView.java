package com.smartbox.investory.investment.reporting.dashboard.application;

import com.smartbox.investory.investment.performance.model.DividendGainer;
import java.util.List;

public record CashFlowView(
    double deposits,
    double withdrawals,
    double cash,
    double realizedProfit,
    double dividends,
    double dividendTax,
    double interest,
    double capitalGainsTax,
    double lossCarryForward,
    List<DividendGainer> dividendGainers) {

  public CashFlowView {
    dividendGainers = dividendGainers == null ? List.of() : List.copyOf(dividendGainers);
  }
}
