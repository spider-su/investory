package com.smartbox.investory.testsupport.happyinvestor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** F4 presentation checkpoints and aliases to independently owned Happy Investor facts. */
public final class HappyInvestorDashboardFacts {
  public enum Strength {
    CANONICAL,
    DERIVED,
    DERIVED_PARTIAL,
    UNDEFINED
  }

  /** Presentation tolerances used by the Dashboard facts report, not production calculations. */
  public static final BigDecimal MONEY_TOLERANCE = new BigDecimal("0.01");

  public static final BigDecimal PERCENT_TOLERANCE = new BigDecimal("0.1");
  public static final BigDecimal CHART_TOLERANCE = new BigDecimal("0.1");

  public static final String MAX_PERIOD = "MAX";
  public static final String YTD_PERIOD = "YTD";
  public static final LocalDate AS_OF_DATE = HappyInvestorBrokerFacts.AS_OF_DATE;
  public static final LocalDate PRICE_OBSERVATION_DATE =
      HappyInvestorBrokerFacts.PRICE_OBSERVATION_DATE;
  public static final String PERFORMANCE_START = "2024-07-31";
  public static final String PERFORMANCE_END = "2025-12-31";
  public static final String YTD_START = "2025-01-01";
  public static final String BENCHMARK_SYMBOL = "SPY";

  public static final String REPORTING_CURRENCY = HappyInvestorTestData.REPORTING_CURRENCY.name();
  public static final BigDecimal BALANCE = HappyInvestorBrokerFacts.MARKET_PORTFOLIO_VALUE;
  public static final BigDecimal NET_DEPOSITS =
      HappyInvestorBrokerFacts.EXTERNAL_CASH_TOTALS.netDepositsPln();
  public static final BigDecimal DEPOSITS =
      HappyInvestorBrokerFacts.EXTERNAL_CASH_TOTALS.depositsPln();
  public static final BigDecimal WITHDRAWALS =
      HappyInvestorBrokerFacts.EXTERNAL_CASH_TOTALS.withdrawalsPln();
  public static final BigDecimal OPEN_POSITIONS_VALUE =
      HappyInvestorBrokerFacts.OPEN_POSITIONS_VALUE;
  public static final BigDecimal OPEN_POSITIONS_UNREALIZED =
      HappyInvestorBrokerFacts.OPEN_POSITIONS_UNREALIZED;
  public static final BigDecimal OPEN_POSITIONS_RETURN_PERCENT =
      OPEN_POSITIONS_UNREALIZED
          .divide(OPEN_POSITIONS_VALUE, 16, RoundingMode.HALF_UP)
          .movePointRight(2);
  public static final BigDecimal APPLE_VALUE = HappyInvestorBrokerFacts.AAPL_VALUE;
  public static final BigDecimal APPLE_UNREALIZED = HappyInvestorBrokerFacts.AAPL_UNREALIZED;
  public static final BigDecimal TESLA_VALUE = HappyInvestorBrokerFacts.TESLA_VALUE;
  public static final BigDecimal TESLA_UNREALIZED = HappyInvestorBrokerFacts.TESLA_UNREALIZED;
  public static final BigDecimal EQUITY_WEIGHT_PERCENT =
      HappyInvestorBrokerFacts.EQUITY_VALUE
          .divide(OPEN_POSITIONS_VALUE, 16, RoundingMode.HALF_UP)
          .movePointRight(2);

  /** Largest holding divided by the open-position total; deliberately calculated, not copied. */
  public static final BigDecimal AAPL_CONCENTRATION_PERCENT =
      APPLE_VALUE.divide(OPEN_POSITIONS_VALUE, 16, RoundingMode.HALF_UP).movePointRight(2);

  public static final String INVESTMENT_RESULT_FORMULA =
      "realized P/L + open-position unrealized P/L + dividends + signed withholding tax + interest; "
          + "all source amounts converted to PLN on their transaction/valuation dates";
  public static final String INVESTMENT_RESULT_SOURCE_FACTS =
      "NATGAS closed result, AAPL/TSLA/open-position unrealized facts, dividend/withholding "
          + "operations, and interest operations";
  public static final String PERIOD_RETURN_METHODOLOGY =
      "compound monthly portfolio returns; MAX uses all available months and YTD uses "
          + "2025-01-01 through 2025-12-31; TWR is the cash-flow-neutral cross-check";

  /** Independent source facts for derived income/realized checks, in their transaction currency. */
  public static final BigDecimal NATGAS_REALIZED_RESULT_USD =
      HappyInvestorBrokerFacts.NATGAS_REALIZED_RESULT_USD;

  public static final BigDecimal DIVIDEND_GROSS_USD = HappyInvestorBrokerFacts.DIVIDEND_GROSS_USD;
  public static final BigDecimal DIVIDEND_WITHHOLDING_TAX_USD =
      HappyInvestorBrokerFacts.DIVIDEND_WITHHOLDING_TAX_USD;
  public static final String REALIZED_RESULT_FORMULA =
      "closed-trade result + swap + applicable commissions, converted on the close date";
  public static final String DIVIDEND_FORMULA =
      "DIVIDEND and DIVIDEND_REVERSAL operations in the selected period, converted on operation date";

  /** HappyInvestor currently has no independent canonical daily series for this Dashboard story. */
  public static final String DRAWDOWN_MISSING_INPUT =
      "canonical daily portfolio values/peaks at start, transaction boundaries, and end";

  public static final String PERFORMANCE_SERIES_MISSING_INPUT =
      "canonical monthly labels and portfolio checkpoints independent of Dashboard output";
  public static final String BENCHMARK_MISSING_INPUT =
      "independent SPY close checkpoints for the MAX and YTD boundaries";

  /** Latest canonical rates visible at the fixed 2025-12-31 read-model boundary. */
  public static final BigDecimal USD_TO_PLN_AT_AS_OF =
      HappyInvestorBrokerFacts.USD_TO_PLN_AT_REFERENCE_DATE;

  public static final BigDecimal EUR_TO_PLN_AT_AS_OF =
      HappyInvestorBrokerFacts.EUR_TO_PLN_AT_REFERENCE_DATE;

  private HappyInvestorDashboardFacts() {}
}
