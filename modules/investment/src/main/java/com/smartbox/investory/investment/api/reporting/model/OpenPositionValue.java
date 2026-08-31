package com.smartbox.investory.investment.api.reporting.model;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenPositionValue {

  private String symbol;
  private BigDecimal volume;
  private BigDecimal costBase;
  private BigDecimal averageOpenPrice;
  private BigDecimal marketPrice;
  private CurrencyType marketPriceCurrency;
  private BigDecimal value;
  private BigDecimal unrealized;
  private BigDecimal profitLossPercent;
  private CurrencyType currency;
  private BigDecimal sharePercent;
  private String priceSource;

  public boolean isTwelveDataPrice() {
    return "TwelveData".equalsIgnoreCase(priceSource);
  }

  public boolean isYahooFinancePrice() {
    return "YahooFinance".equalsIgnoreCase(priceSource);
  }

  public OpenPositionValue(
      String symbol,
      double volume,
      double costBase,
      double averageOpenPrice,
      double value,
      double unrealized,
      Double profitLossPercent,
      CurrencyType currency,
      double sharePercent) {
    this(
        symbol,
        BigDecimal.valueOf(volume),
        BigDecimal.valueOf(costBase),
        BigDecimal.valueOf(averageOpenPrice),
        BigDecimal.valueOf(averageOpenPrice),
        currency,
        BigDecimal.valueOf(value),
        BigDecimal.valueOf(unrealized),
        profitLossPercent == null ? null : BigDecimal.valueOf(profitLossPercent),
        currency,
        BigDecimal.valueOf(sharePercent),
        null);
  }
}
