package com.smartbox.investory.testsupport.happyinvestor;

import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.portfolio.PortfolioTestData.AssetDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/** Central identity and assumption constants for the cross-domain reference investor. */
public final class HappyInvestorTestData {
  public static final String DISPLAY_NAME = "Happy Investor";
  public static final String PORTFOLIO_NAME = "Happy Investor Portfolio";
  public static final long PORTFOLIO_ID = 1L;
  public static final CurrencyType REPORTING_CURRENCY = CurrencyType.PLN;
  public static final ZoneId TIMEZONE = ZoneId.of("Europe/Warsaw");
  public static final LocalDate HISTORY_START = LocalDate.of(2024, 7, 31);
  public static final LocalDate REFERENCE_DATE = LocalDate.of(2025, 12, 31);

  public static final BigDecimal CASH_RESERVE = new BigDecimal("50000.00");
  public static final String APARTMENT_A_NAME = "Apartment A";
  public static final String APARTMENT_B_NAME = "Apartment B";
  public static final String FAMILY_CAR_NAME = "Family Car";
  public static final BigDecimal APARTMENT_A_VALUE = new BigDecimal("400000");
  public static final BigDecimal APARTMENT_B_VALUE = new BigDecimal("500000");
  public static final BigDecimal FAMILY_CAR_VALUE = new BigDecimal("10000");
  public static final LocalDate APARTMENT_ACQUISITION_DATE = LocalDate.of(2024, 8, 1);
  public static final LocalDate APARTMENT_B1_START = LocalDate.of(2024, 8, 1);
  public static final LocalDate APARTMENT_B1_END = LocalDate.of(2025, 6, 30);
  public static final LocalDate APARTMENT_B2_START = LocalDate.of(2025, 7, 1);
  public static final BigDecimal APARTMENT_A_MONTHLY_RENT = new BigDecimal("3200");
  public static final BigDecimal APARTMENT_B1_MONTHLY_RENT = new BigDecimal("2800");
  public static final BigDecimal APARTMENT_B2_MONTHLY_RENT = new BigDecimal("3000");
  public static final BigDecimal IBKR_TRADE_COMMISSION = new BigDecimal("1.00");
  public static final BigDecimal XTB_TRADE_COMMISSION = BigDecimal.ZERO;
  public static final BigDecimal INVESTMENT_TAX = new BigDecimal("0.19");
  public static final BigDecimal RENTAL_TAX = new BigDecimal("0.085");
  public static final BigDecimal INFLATION = new BigDecimal("0.025");

  /** Values copied from V01.003__initial_data.sql; EUR/PLN is derived, never seeded. */
  public static final BigDecimal EUR_USD_AT_HISTORY_START = new BigDecimal("1.082239");

  public static final BigDecimal USD_PLN_AT_HISTORY_START = new BigDecimal("3.9689");
  public static final BigDecimal EUR_USD_TRANSFER_AMOUNT = new BigDecimal("4328.956");
  public static final BigDecimal EUR_PLN_AT_HISTORY_START = new BigDecimal("4.2952983671");
  public static final BigDecimal EUR_PLN_TRANSFER_AMOUNT = new BigDecimal("17181.1934684000");
  public static final BigDecimal PLN_USD_TRANSFER_RATE = new BigDecimal("0.2519589810778805");
  public static final BigDecimal PLN_USD_TRANSFER_AMOUNT = new BigDecimal("125.9794905389403");

  /** PostgreSQL NUMERIC(30,8) representation of {@link #PLN_USD_TRANSFER_AMOUNT}. */
  public static final BigDecimal PLN_USD_TRANSFER_PERSISTED_AMOUNT = new BigDecimal("125.97949054");

  public static final long IBKR_USD_ACCOUNT_ID = 17959259L;
  public static final long XTB_USD_ACCOUNT_ID = 51499241L;
  public static final long XTB_PLN_ACCOUNT_ID = 51551301L;
  public static final long XTB_EUR_ACCOUNT_ID = 51548444L;

  public static final String WIG20_ETF_SYMBOL = "ETFBW20TR.PL";
  public static final AssetDefinition TREASURY_2026 =
      new AssetDefinition(
          "US91282CKB62", "US91282CKB62", "T458022826", null, "US", CurrencyType.USD, "BOND");
  public static final AssetDefinition TREASURY_2033 =
      new AssetDefinition(
          "US91282CRC72", "US91282CRC72", "T438073133", null, "US", CurrencyType.USD, "BOND");
  public static final String NATGAS_SYMBOL = "NATGAS";
  public static final BigDecimal NATGAS_NET_RESULT = new BigDecimal("19.12");
  public static final BigDecimal NATGAS_GROSS_RESULT = new BigDecimal("105.90");
  public static final BigDecimal NATGAS_ROLLOVER = new BigDecimal("-86.10");
  public static final BigDecimal NATGAS_SWAP = new BigDecimal("-0.68");
  // Realized trade cash: gross close (105.90) net of rollover (-86.10) = 19.80 = net result - swap.
  public static final BigDecimal NATGAS_CLOSE_TRADE = new BigDecimal("19.80");
  public static final LocalDate NATGAS_CLOSE_DATE = LocalDate.of(2025, 9, 26);

  private HappyInvestorTestData() {}

  public static BigDecimal eurPlnAtHistoryStart() {
    return EUR_USD_AT_HISTORY_START.multiply(USD_PLN_AT_HISTORY_START);
  }
}
