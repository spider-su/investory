package com.smartbox.investory.investment.api.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Public period income view backed by the canonical monthly brokerage reporting projection. */
public record BrokerageIncomeSnapshot(
    CurrencyType baseCurrency,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal startValue,
    BigDecimal endValue,
    BigDecimal dividends,
    BigDecimal interest,
    BigDecimal taxes) {

  public BrokerageIncomeSnapshot {
    startValue = nz(startValue);
    endValue = nz(endValue);
    dividends = nz(dividends);
    interest = nz(interest);
    taxes = nz(taxes);
  }

  public BigDecimal netIncome() {
    return dividends.add(interest).subtract(taxes);
  }

  private static BigDecimal nz(BigDecimal value) {
    return com.smartbox.investory.shared.util.BigDecimalUtils.zeroIfNull(value);
  }
}
