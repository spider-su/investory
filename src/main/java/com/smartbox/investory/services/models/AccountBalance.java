package com.smartbox.investory.services.models;

import com.smartbox.investory.shared.currency.CurrencyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalance {

  private Long accountId;
  private String accountName;
  private Double netDeposit;
  private Double baseNetDeposit;
  private Double profit;
  private Double localProfit;
  private Double profitLossPercent;
  private double balance;
  private double cash;
  private CurrencyType localCurrency;
  private Double localBalance;
  private Double localCash;
}
