package com.smartbox.investory.testsupport.happyinvestor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Detects drift between the committed FastDatabase snapshot and canonical source facts. */
class HappyInvestorSchemaCanonicalTest {
  @Test
  void snapshotContainsTheCanonicalNonInvestmentStory() throws IOException {
    String snapshot = resource("db/snapshot/schema.sql");
    String common = resource("db/snapshot/happyinvestor-common.sql");
    String broker = resource("db/snapshot/happyinvestor-broker.sql");

    assertTrue(common.contains("(2, 'happy.investor', 'Happy Investor'"));
    assertTrue(common.contains("(2, 'Happy Investor Portfolio', 'PLN', 'PLN'"));
    assertTrue(snapshot.contains("2\tHappy Investor Portfolio\tPLN\tPLN"));

    List<String> canonicalAccounts =
        List.of(
            "(91000001, '90000001', 'USD', 'IBKR', 'IBKR USD investment account', 'Happy Investor', 2, false)",
            "(91000002, '90000002', 'USD', 'XTB', 'XTB USD investment account', 'Happy Investor', 2, false)",
            "(91000003, '90000003', 'PLN', 'XTB', 'XTB PLN investment account', 'Happy Investor', 2, false)",
            "(91000004, '90000009', 'EUR', 'XTB', 'XTB EUR cash-only account', 'Happy Investor', 2, true)");
    canonicalAccounts.forEach(account -> assertTrue(common.contains(account), account));
    assertTrue(common.contains("base_currency = EXCLUDED.base_currency"));
    assertTrue(common.contains("VALUES (2, 'Happy Investor Portfolio', 'PLN', 'PLN'"));

    assertTrue(snapshot.contains("9401\t2\tCash reserve\tPLN\t25000.000000000000"));
    assertTrue(snapshot.contains("9402\t2\tApartment A\tPLN\t400000.000000000000"));
    assertTrue(snapshot.contains("9403\t2\tApartment B\tPLN\t500000.000000000000"));
    assertTrue(snapshot.contains("9404\t2\tFamily Car\tVEHICLE\tPLN\t10000.000000000000"));
    assertTrue(snapshot.contains("9405\t2\tTreasury 2026\tPLN\t10000.000000000000"));
    assertTrue(
        snapshot.contains(
            "9407\t2\tUnited States Treasury 4 3/8 07/31/33\tPLN\t10000.000000000000"));
    assertTrue(snapshot.contains("9406\t2\tTerm cash reserve\tPLN\t25000.000000000000"));
    assertTrue(snapshot.contains("9501\t9402\t2024-08-01"));
    assertTrue(snapshot.contains("9502\t9403\t2024-08-01\t2025-06-30"));
    assertTrue(snapshot.contains("9503\t9403\t2025-07-01"));
    assertTrue(snapshot.contains("1\t9501\tRENT\t3200.000000000000\tMONTHLY\tf"));
    assertTrue(snapshot.contains("3\t9503\tRENT\t3000.000000000000\tMONTHLY\tf"));
    assertTrue(snapshot.contains("Annual rental-tax base"));
    assertTrue(snapshot.contains("7001\t91000001\tDEPOSIT"));
    assertTrue(snapshot.contains("7101\t91000001\t1\tAAPL.US"));
    assertTrue(snapshot.contains("7106\t91000002\t1001\tTSLA.US"));
    assertTrue(snapshot.contains("7103\t91000001\t1151\tVWRA.UK"));
    assertTrue(snapshot.contains("7105\t91000002\t651\tNVDA.US"));
    assertTrue(snapshot.contains("7107\t91000003\t251\tGOOGL.US"));
    // Broker positions must stay in lockstep with HappyInvestorScenario: MSFT is an open IBKR
    // holding and NATGAS is the closed RESULT_ONLY CFD lot on the XTB USD account.
    assertTrue(snapshot.contains("7108\t91000001\t451\tMSFT.US\tMSFT"));
    assertTrue(snapshot.contains("7110\t91000002\t501\tNATGAS\tNATGAS"));
    assertTrue(snapshot.contains("BUY\tRESULT_ONLY\t0.01000000"));
    assertTrue(snapshot.contains("9405\t2\tTreasury 2026\tPLN\t10000.000000000000"));
    assertTrue(snapshot.contains("9406\t2\tTerm cash reserve\tPLN\t25000.000000000000"));
    assertTrue(snapshot.contains("\t159307.015664000000\t970000.000000000000\t74400.000000000000"));
    assertTrue(snapshot.contains("9201\t2\tHappy Investor Plan\t1984-01-01\t2024\t85\t60"));
    assertTrue(snapshot.contains("9301\t2\t2025\tDRAFT\t"));

    assertTrue(common.contains("(9401, 2, 'Cash reserve'"));
    assertTrue(common.contains("(9406, 2, 'Term cash reserve'"));
    assertTrue(common.contains("(9407, 2, 'United States Treasury 4 3/8 07/31/33'"));
    assertTrue(common.contains("(9501, 9402, DATE '2024-08-01'"));
    assertTrue(common.contains("(9402, 2, 'Apartment A', 'PLN', 400000, 3200, DATE '2024-08-01'"));
    assertTrue(common.contains("(9503, 9403, DATE '2025-07-01', NULL, NULL, false"));
    assertTrue(common.contains("(9201, 2, 'Happy Investor Plan'"));
    assertTrue(
        common.contains("2024, 85, 60, 90000, 12000,\n" + "    36000, 6000, 0.025, 0.025, 0.035"));
    assertTrue(common.contains("'CASH,BONDS,STOCKS', 2, 0.05, 0.25, true"));
    assertTrue(common.contains("0.035, 0.07, 67, 24000, 0.19"));
    assertTrue(common.contains("2025, 50000, 159307.015664, 970000, 74400, 74400, 1"));
    assertTrue(broker.contains("(7108, 91000001, 451, 'MSFT.US', 'MSFT', 'BUY', 'CASH_SETTLED'"));
    assertTrue(broker.contains("(7110, 91000002, 501, 'NATGAS', 'NATGAS', 'BUY', 'RESULT_ONLY'"));
    assertTrue(broker.contains("(7111, 91000001, 1201, 'US91282CKB62'"));
    assertTrue(broker.contains("(7112, 91000001, 1251, 'US91282CRC72'"));
    assertTrue(broker.contains("'HAPPYINVESTOR_FIXTURE', 'US91282CKB62'"));
    assertTrue(broker.contains("'FIXTURE_PERCENT_OF_PAR'"));
    assertTrue(!broker.contains("'AMZN.US'"), "AMZN must not be a seeded HappyInvestor position");
    assertTrue(!broker.contains("'META.US'"), "META must not be a seeded HappyInvestor position");
    assertTrue(!broker.contains("'O.US'"), "O must not be a seeded HappyInvestor position");
    assertTrue(broker.contains("'CLOSE_TRADE', 501, 'NATGAS'"));
    assertTrue(broker.contains("105.90 net of -86.10 rollover"));
    assertTrue(broker.contains("19.80, 'USD'"));
    assertTrue(broker.contains("-0.68, 'USD'"));
    assertTrue(broker.contains("19.12"));
    assertTrue(broker.contains("'Full call redemption principal returned'"));
    assertTrue(broker.contains("'Next-day Treasury principal reinvestment'"));
    assertTrue(common.contains("DATE '2026-02-28'"));
    assertTrue(common.contains("DATE '2026-03-01'"));
    assertTrue(broker.contains("'EUR', 'PLN', 4.2952983671"));
    assertTrue(broker.contains("17181.1934684000, 'PLN'"));
    assertTrue(broker.contains("'PLN', 'USD', 0.2519589810778805"));
    assertTrue(broker.contains("125.9794905389403, 'USD'"));
    assertTrue(broker.contains("'US91282CRC72'"));
    assertTrue(snapshot.contains("2025-12-31\tUSD\tPLN\t3.60160000"));
    assertTrue(snapshot.contains("2025-01-01\tSTOOQ\taapl.us"));
    assertTrue(snapshot.contains("2025-01-01\tSTOOQ\tvwra.uk"));
    assertTrue(
        common.contains(
            "2025, 50000, "
                + HappyInvestorPlanFacts.BASELINE_INVESTMENT_CAPITAL.toPlainString()
                + ", "
                + HappyInvestorPlanFacts.BASELINE_LONG_TERM_CAPITAL.toPlainString()
                + ", "
                + HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_GROSS_ANNUAL.toPlainString()
                + ", "
                + HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_GROSS_ANNUAL.toPlainString()
                + ", 1"));

    // The generated snapshot must retain representative rows from both canonical overlays.
    List<String> generatedMarkers =
        List.of(
            "Happy Investor Portfolio",
            "IBKR USD investment account",
            "XTB EUR cash-only account",
            "Apartment A",
            "Happy Investor Plan",
            "EUR-USD-2024-07-31",
            "EUR-PLN-2024-07-31",
            "PLN-USD-2025-03",
            "USD-PLN-2025-03",
            "NATGAS CFD 2040572606",
            "US91282CRC72");
    generatedMarkers.forEach(
        marker -> assertTrue(snapshot.contains(marker), "stale snapshot: " + marker));
  }

  private static String resource(String path) throws IOException {
    try (InputStream stream =
        HappyInvestorSchemaCanonicalTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing canonical resource: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
