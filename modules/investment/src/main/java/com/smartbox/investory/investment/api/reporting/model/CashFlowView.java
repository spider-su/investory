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
    List<DividendGainer> dividendGainers,
    AccountFundingView accountFunding) {

  public CashFlowView(
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
    this(
        deposits,
        withdrawals,
        cash,
        realizedProfit,
        dividends,
        dividendTax,
        interest,
        capitalGainsTax,
        lossCarryForward,
        dividendGainers,
        null);
  }

  public CashFlowView {
    dividendGainers =
        com.smartbox.investory.shared.util.CollectionUtils.immutableListOrEmpty(dividendGainers);
  }
}
