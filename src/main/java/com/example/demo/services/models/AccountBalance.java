package com.example.demo.services.models;

import com.example.demo.infrastructure.CurrencyType;
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
  private Double profitLossPercent;
  private double balance;
  private double cash;
  private CurrencyType localCurrency;
  private Double localBalance;
}
