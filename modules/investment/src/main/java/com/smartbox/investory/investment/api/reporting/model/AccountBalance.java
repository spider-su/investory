package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalance {

  private Long accountId;
  private String accountName;
  private BigDecimal netDeposit;
  private BigDecimal baseNetDeposit;
  private BigDecimal profit;
  private BigDecimal localProfit;
  private BigDecimal profitLossPercent;
  private BigDecimal balance;
  private BigDecimal cash;
  private CurrencyType localCurrency;
  private BigDecimal localBalance;
  private BigDecimal localCash;

  public AccountBalance(
      Long accountId,
      String accountName,
      Double netDeposit,
      Double baseNetDeposit,
      Double profit,
      Double localProfit,
      Double profitLossPercent,
      double balance,
      double cash,
      CurrencyType localCurrency,
      Double localBalance,
      Double localCash) {
    this(
        accountId,
        accountName,
        decimal(netDeposit),
        decimal(baseNetDeposit),
        decimal(profit),
        decimal(localProfit),
        decimal(profitLossPercent),
        BigDecimal.valueOf(balance),
        BigDecimal.valueOf(cash),
        localCurrency,
        decimal(localBalance),
        decimal(localCash));
  }

  private static BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
