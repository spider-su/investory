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
        decimal(localCash),
        null);
  }

  /** Compatibility alias: this value is account-native, not portfolio-base. */
  @Deprecated(forRemoval = false)
  public BigDecimal getNetDeposit() {
    return netDepositLocal;
  }

  /** Compatibility alias for the portfolio-base deposit. */
  public BigDecimal getBaseNetDeposit() {
    return netDepositBase;
  }

  /** Compatibility alias: this value is the portfolio-base economic result. */
  @Deprecated(forRemoval = false)
  public BigDecimal getProfit() {
    return profitBase;
  }

  /** Compatibility alias for the account-native result. */
  public BigDecimal getLocalProfit() {
    return profitLocal;
  }

  /** Compatibility alias for the portfolio-base balance. */
  @Deprecated(forRemoval = false)
  public BigDecimal getBalance() {
    return balanceBase;
  }

  /** Compatibility alias for the portfolio-base cash balance. */
  @Deprecated(forRemoval = false)
  public BigDecimal getCash() {
    return cashBase;
  }

  /** Compatibility alias for the account-native balance. */
  public BigDecimal getLocalBalance() {
    return balanceLocal;
  }

  /** Compatibility alias for the account-native cash balance. */
  public BigDecimal getLocalCash() {
    return cashLocal;
  }

  private static BigDecimal decimal(Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
