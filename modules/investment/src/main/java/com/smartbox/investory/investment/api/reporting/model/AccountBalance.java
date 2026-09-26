package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AccountBalance {

  private Long accountId;
  private String accountName;
  private BigDecimal netDepositLocal;
  private BigDecimal netDepositBase;
  private BigDecimal profitBase;
  private BigDecimal profitLocal;
  private BigDecimal profitLossPercent;
  private BigDecimal balanceBase;
  private BigDecimal cashBase;
  private CurrencyType localCurrency;
  private BigDecimal balanceLocal;
  private BigDecimal cashLocal;
  private BigDecimal fxEffect;
}
