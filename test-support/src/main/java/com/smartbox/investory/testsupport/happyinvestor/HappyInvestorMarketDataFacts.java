package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit provider-refresh facts layered on the canonical HappyInvestor history. */
public final class HappyInvestorMarketDataFacts {
  public static final LocalDate REFRESH_DATE = LocalDate.of(2026, 8, 20);
  public static final String PROVIDER = "TWELVE_DATA";
  public static final String PRICE_ORIGIN = "TWELVE_DATA_MARKET_CLOSE";
  public static final BigDecimal GOOGL_CLOSE = new BigDecimal("249.059");
  public static final BigDecimal TESLA_CLOSE = new BigDecimal("403.840");

  private HappyInvestorMarketDataFacts() {}
}
