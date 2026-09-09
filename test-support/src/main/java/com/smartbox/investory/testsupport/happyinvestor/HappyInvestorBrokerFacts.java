package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Independently calculated broker facts for the complete HappyInvestor source story. */
public final class HappyInvestorBrokerFacts {
  public record Position(
      String symbol, BigDecimal quantity, BigDecimal quotedPrice, BigDecimal costBasis) {
    public BigDecimal marketValuePln() {
      BigDecimal quoteValue =
          symbol.startsWith("US91282")
              ? quantity.multiply(quotedPrice).movePointLeft(2)
              : quantity.multiply(quotedPrice);
      return quoteValue.multiply(USD_TO_PLN_AT_REFERENCE_DATE);
    }

    public BigDecimal unrealizedPln() {
      return marketValuePln().subtract(costBasis.multiply(USD_TO_PLN_AT_REFERENCE_DATE));
    }
  }

  public static final LocalDate AS_OF_DATE = HappyInvestorTestData.REFERENCE_DATE;
  public static final LocalDate PRICE_OBSERVATION_DATE = LocalDate.of(2024, 12, 31);
  public static final BigDecimal USD_TO_PLN_AT_REFERENCE_DATE = new BigDecimal("3.6016");
  public static final BigDecimal EUR_TO_USD_AT_REFERENCE_DATE = new BigDecimal("1.173562");
  public static final BigDecimal EUR_TO_PLN_AT_REFERENCE_DATE =
      EUR_TO_USD_AT_REFERENCE_DATE.multiply(USD_TO_PLN_AT_REFERENCE_DATE);

  public static final List<Position> OPEN_POSITIONS =
      List.of(
          new Position(
              "AAPL.US", new BigDecimal("150"), new BigDecimal("249.059"), new BigDecimal("28000")),
          new Position(
              "VWRA.UK", new BigDecimal("30"), new BigDecimal("139.340"), new BigDecimal("3700")),
          new Position(
              "NVDA.US", new BigDecimal("10"), new BigDecimal("134.290"), new BigDecimal("1000")),
          new Position("TSLA.US", BigDecimal.ONE, new BigDecimal("403.840"), new BigDecimal("200")),
          new Position(
              "GOOGL.US", new BigDecimal("5"), new BigDecimal("189.300"), new BigDecimal("750")),
          new Position(
              "MSFT.US", new BigDecimal("10"), new BigDecimal("421.500"), new BigDecimal("1000")),
          new Position(
              "US91282CKB62", new BigDecimal("10000"), BigDecimal.ONE, new BigDecimal("10000")));

  public static final BigDecimal OPEN_POSITIONS_VALUE =
      OPEN_POSITIONS.stream()
          .map(Position::marketValuePln)
          .reduce(BigDecimal.ZERO, BigDecimal::add);

  public static final BigDecimal OPEN_POSITIONS_UNREALIZED =
      OPEN_POSITIONS.stream().map(Position::unrealizedPln).reduce(BigDecimal.ZERO, BigDecimal::add);

  /**
   * Portfolio-level brokerage cash after the explicit boundary withdrawals. Internal FX-transfer
   * legs are netted and do not change portfolio wealth.
   */
  public static final BigDecimal BROKERAGE_CASH = BigDecimal.ZERO;

  public static final BigDecimal MARKET_PORTFOLIO_VALUE = OPEN_POSITIONS_VALUE.add(BROKERAGE_CASH);
  public static final BigDecimal AAPL_VALUE = OPEN_POSITIONS.get(0).marketValuePln();
  public static final BigDecimal LARGEST_HOLDING_CONCENTRATION =
      AAPL_VALUE.divide(OPEN_POSITIONS_VALUE, 16, RoundingMode.HALF_UP).movePointRight(2);

  public static final BigDecimal EQUITY_VALUE =
      OPEN_POSITIONS.stream()
          .filter(
              position ->
                  List.of("AAPL.US", "NVDA.US", "TSLA.US", "GOOGL.US", "MSFT.US")
                      .contains(position.symbol()))
          .map(Position::marketValuePln)
          .reduce(BigDecimal.ZERO, BigDecimal::add);
  public static final BigDecimal ETF_VALUE = OPEN_POSITIONS.get(1).marketValuePln();
  public static final BigDecimal FIXED_INCOME_VALUE = OPEN_POSITIONS.get(6).marketValuePln();

  private HappyInvestorBrokerFacts() {}
}
