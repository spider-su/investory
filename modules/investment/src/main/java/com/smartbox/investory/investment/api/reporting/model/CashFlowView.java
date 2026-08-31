package com.smartbox.investory.investment.api.reporting.model;

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
    dividendGainers =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(dividendGainers);
  }
}
