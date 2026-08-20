package com.smartbox.investory.services.models;

import com.smartbox.investory.shared.currency.CurrencyType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenPositionValue {

  private String symbol;
  private double volume;
  private double costBase;
  private double averageOpenPrice;
  private double marketPrice;
  private CurrencyType marketPriceCurrency;
  private double value;
  private double unrealized;
  private Double profitLossPercent;
  private CurrencyType currency;
  private double sharePercent;
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
        volume,
        costBase,
        averageOpenPrice,
        averageOpenPrice,
        currency,
        value,
        unrealized,
        profitLossPercent,
        currency,
        sharePercent,
        null);
  }
}
