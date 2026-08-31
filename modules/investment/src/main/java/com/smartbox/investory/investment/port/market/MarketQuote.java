package com.smartbox.investory.investment.port.market;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Provider-neutral quote payload crossing the market-data port. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MarketQuote {
  private String symbol;
  private String name;
  private String exchange;
  private String currency;
  private String datetime;
  private double open;
  private double high;
  private double low;
  private double close;
  private long volume;
  private double previousClose;
  private double change;
  private double percentChange;
  private boolean isMarketOpen;
}
