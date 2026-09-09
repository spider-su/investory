package com.smartbox.investory.testsupport.happyinvestor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Detects drift between the committed FastDatabase snapshot and canonical source facts. */
class HappyInvestorSchemaCanonicalTest {
  @Test
  void snapshotContainsTheCanonicalNonInvestmentStory() throws IOException {
    String snapshot = resource("db/snapshot/schema.sql");
    String common = resource("db/snapshot/happyinvestor-common.sql");
    String broker = resource("db/snapshot/happyinvestor-broker.sql");

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
    assertTrue(snapshot.contains("7001\t2017959259\tDEPOSIT"));
    assertTrue(snapshot.contains("7101\t2017959259\t1\tAAPL.US"));
    assertTrue(snapshot.contains("7106\t2051499241\t1001\tTSLA.US"));
    assertTrue(snapshot.contains("7103\t2017959259\t1151\tVWRA.UK"));
    assertTrue(snapshot.contains("7105\t2051499241\t651\tNVDA.US"));
    assertTrue(snapshot.contains("7107\t2051551301\t251\tGOOGL.US"));
    // Broker positions must stay in lockstep with HappyInvestorScenario: MSFT is an open IBKR
    // holding and NATGAS is the closed RESULT_ONLY CFD lot on the XTB USD account.
    assertTrue(snapshot.contains("7108\t2017959259\t451\tMSFT.US\tMSFT"));
    assertTrue(snapshot.contains("7110\t2051499241\t501\tNATGAS\tNATGAS"));
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
    assertTrue(broker.contains("(7108, 2017959259, 451, 'MSFT.US', 'MSFT', 'BUY', 'CASH_SETTLED'"));
    assertTrue(broker.contains("(7110, 2051499241, 501, 'NATGAS', 'NATGAS', 'BUY', 'RESULT_ONLY'"));
    assertTrue(broker.contains("(7111, 2017959259, 1201, 'US91282CKB62'"));
    assertTrue(broker.contains("(7112, 2017959259, 1251, 'US91282CRC72'"));
    assertTrue(!broker.contains("'AMZN.US'"), "AMZN must not be a seeded HappyInvestor position");
    assertTrue(!broker.contains("'META.US'"), "META must not be a seeded HappyInvestor position");
    assertTrue(!broker.contains("'O.US'"), "O must not be a seeded HappyInvestor position");
    assertTrue(broker.contains("'CLOSE_TRADE', 501, 'NATGAS'"));
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
  }

  private static String resource(String path) throws IOException {
    try (InputStream stream =
        HappyInvestorSchemaCanonicalTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing canonical resource: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
