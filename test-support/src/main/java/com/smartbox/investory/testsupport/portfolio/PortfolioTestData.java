package com.smartbox.investory.testsupport.portfolio;

import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/** Canonical deterministic portfolio values shared by tests. */
public final class PortfolioTestData {

  public static final ZoneId TEST_ZONE = ZoneId.of("Europe/Warsaw");

  public static final LocalDate PERIOD_START = LocalDate.of(2025, 1, 1);
  public static final LocalDate JANUARY_DEPOSIT_DATE = LocalDate.of(2025, 1, 2);
  public static final LocalDate AAPL_FIRST_BUY_DATE = LocalDate.of(2025, 1, 8);
  public static final LocalDate JANUARY_MONTH_END = LocalDate.of(2025, 1, 31);
  public static final LocalDate AAPL_SECOND_BUY_DATE = LocalDate.of(2025, 2, 12);
  public static final LocalDate FEBRUARY_MONTH_END = LocalDate.of(2025, 2, 28);
  public static final LocalDate AAPL_PARTIAL_SALE_DATE = LocalDate.of(2025, 5, 10);
  public static final LocalDate MID_YEAR = LocalDate.of(2025, 6, 30);
  public static final LocalDate YEAR_END = LocalDate.of(2025, 12, 31);
  public static final LocalDate SECOND_YEAR_END = LocalDate.of(2026, 12, 31);

  public static final long IBKR_USD_ACCOUNT_ID = 17959259L;
  public static final long XTB_EUR_ACCOUNT_ID = 51499241L;
  public static final long POLISH_BONDS_PLN_ACCOUNT_ID = 51551301L;
  public static final long CRYPTO_USD_ACCOUNT_ID = 53582946L;

  /** Migration-backed identities for the complete reference scenario. */
  public static final long HAPPY_XTB_USD_ACCOUNT_ID = 51499241L;

  public static final long HAPPY_XTB_PLN_ACCOUNT_ID = 51551301L;
  public static final long HAPPY_XTB_EUR_ACCOUNT_ID = 51548444L;

  public static final double DEFAULT_USD_DEPOSIT = 100_000.00;
  public static final double DEFAULT_EUR_DEPOSIT = 25_000.00;
  public static final double DEFAULT_PLN_DEPOSIT = 50_000.00;
  public static final double AAPL_FIRST_BUY_QUANTITY = 100.0;
  public static final double AAPL_SECOND_BUY_QUANTITY = 50.0;
  public static final double AAPL_FIRST_BUY_PRICE = 180.00;
  public static final double AAPL_SECOND_BUY_PRICE = 200.00;
  public static final double AAPL_FIRST_BUY_COMMISSION = -5.00;
  public static final double AAPL_SECOND_BUY_COMMISSION = -3.00;
  public static final double AAPL_PARTIAL_SALE_QUANTITY = 60.0;
  public static final double AAPL_PARTIAL_SALE_PRICE = 220.00;
  public static final double AAPL_PARTIAL_SALE_COMMISSION = -4.00;
  public static final double AAPL_DIVIDEND_GROSS = 75.00;
  public static final double AAPL_WITHHOLDING_TAX = -11.25;

  public static final AccountDefinition IBKR_USD =
      new AccountDefinition(
          IBKR_USD_ACCOUNT_ID, "IBKR USD", CurrencyType.USD, "Interactive Brokers");
  public static final AccountDefinition XTB_EUR =
      new AccountDefinition(XTB_EUR_ACCOUNT_ID, "XTB EUR", CurrencyType.EUR, "XTB");
  public static final AccountDefinition POLISH_BONDS_PLN =
      new AccountDefinition(
          POLISH_BONDS_PLN_ACCOUNT_ID, "Polish Bonds PLN", CurrencyType.PLN, "Treasury Direct");
  public static final AccountDefinition CRYPTO_USD =
      new AccountDefinition(
          CRYPTO_USD_ACCOUNT_ID, "Crypto USD", CurrencyType.USD, "Crypto Exchange");

  public static final AssetDefinition AAPL =
      new AssetDefinition(
          "AAPL.US", "AAPL", "AAPL", "AAPL", "United States", CurrencyType.USD, "STOCK");
  public static final AssetDefinition MSFT =
      new AssetDefinition(
          "MSFT.US", "MSFT", "MSFT", "MSFT", "United States", CurrencyType.USD, "STOCK");
  public static final AssetDefinition TSLA =
      new AssetDefinition(
          "TSLA.US", "TSLA", "TSLA", "TSLA", "United States", CurrencyType.USD, "STOCK");
  public static final AssetDefinition SPY =
      new AssetDefinition("SPY.US", "SPY", "SPY", "SPY", "United States", CurrencyType.USD, "ETF");
  public static final AssetDefinition SIE_DE =
      new AssetDefinition("SIE.DE", "SIE", "SIE", "SIE.DE", "Germany", CurrencyType.EUR, "STOCK");
  public static final AssetDefinition PKO_WA =
      new AssetDefinition("PKO.PL", "PKO", "PKO", "PKO.WA", "Poland", CurrencyType.PLN, "STOCK");
  public static final AssetDefinition IWDA_AS =
      new AssetDefinition(
          "IWDA.NL", "IWDA", "IWDA", "IWDA.AS", "Netherlands", CurrencyType.EUR, "ETF");
  public static final AssetDefinition US_TREASURY_2028 =
      new AssetDefinition(
          "UST2028.US", "UST2028", "UST2028", "UST2028", "United States", CurrencyType.USD, "BOND");
  public static final AssetDefinition POLISH_TREASURY_2027 =
      new AssetDefinition(
          "PLTB2027.PL", "PLTB2027", "PLTB2027", "PLTB2027", "Poland", CurrencyType.PLN, "BOND");
  public static final AssetDefinition AAPL_CALL_2026 =
      new AssetDefinition(
          "AAPL260116C00180000",
          "AAPL",
          "AAPL",
          "AAPL260116C00180000",
          "United States",
          CurrencyType.USD,
          "OPTION");
  public static final AssetDefinition SPY_PUT_2026 =
      new AssetDefinition(
          "SPY260116P00400000",
          "SPY",
          "SPY",
          "SPY260116P00400000",
          "United States",
          CurrencyType.USD,
          "OPTION");
  public static final AssetDefinition BTC =
      new AssetDefinition("BTC-USD", "BTC", "BTC", "BTC-USD", "Crypto", CurrencyType.USD, "CRYPTO");
  public static final AssetDefinition ETH =
      new AssetDefinition("ETH-USD", "ETH", "ETH", "ETH-USD", "Crypto", CurrencyType.USD, "CRYPTO");

  public static final Map<LocalDate, Double> AAPL_PRICES_USD =
      Map.of(
          AAPL_FIRST_BUY_DATE,
          180.00,
          JANUARY_MONTH_END,
          190.00,
          AAPL_SECOND_BUY_DATE,
          200.00,
          FEBRUARY_MONTH_END,
          210.00,
          AAPL_PARTIAL_SALE_DATE,
          220.00,
          YEAR_END,
          230.00,
          LocalDate.of(2026, 2, 10),
          210.00);

  public static final Map<LocalDate, Double> EUR_USD =
      Map.of(PERIOD_START, 1.10, MID_YEAR, 1.08, YEAR_END, 1.05, LocalDate.of(2026, 6, 30), 1.12);

  public static final Map<LocalDate, Double> PLN_USD =
      Map.of(
          PERIOD_START,
          0.2500,
          MID_YEAR,
          0.2450,
          YEAR_END,
          0.2400,
          LocalDate.of(2026, 6, 30),
          0.2600);

  private PortfolioTestData() {}

  public static ZonedDateTime atNoon(LocalDate date) {
    return date.atTime(12, 0).atZone(TEST_ZONE);
  }

  public record AccountDefinition(Long id, String name, CurrencyType currency, String broker) {}

  public record AssetDefinition(
      String symbol,
      String ticker,
      String ibkr,
      String yahoo,
      String country,
      CurrencyType currency,
      String assetType) {}
}
