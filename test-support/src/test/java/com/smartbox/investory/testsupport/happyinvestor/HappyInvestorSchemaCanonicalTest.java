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

    assertTrue(snapshot.contains("9401\t1\tCash reserve\tCASH_RESERVE\tPLN"));
    assertTrue(snapshot.contains("9402\t1\tApartment A\tREAL_ESTATE\tPLN"));
    assertTrue(snapshot.contains("9403\t1\tApartment B\tREAL_ESTATE\tPLN"));
    assertTrue(snapshot.contains("9404\t1\tFamily Car\tOTHER\tPLN"));
    assertTrue(snapshot.contains("9501\t9402\t2024-08-01"));
    assertTrue(snapshot.contains("9502\t9403\t2024-08-01\t2025-06-30"));
    assertTrue(snapshot.contains("9503\t9403\t2025-07-01"));
    assertTrue(snapshot.contains("0.085000000000"));
    assertTrue(snapshot.contains("7001\t17959259\tDEPOSIT"));
    assertTrue(snapshot.contains("7106\t51499241\t1001\tTSLA.US"));
    // Broker positions must stay in lockstep with HappyInvestorScenario: MSFT is an open IBKR
    // holding and NATGAS is the closed RESULT_ONLY CFD lot on the XTB USD account.
    assertTrue(snapshot.contains("7108\t17959259\t451\tMSFT.US\tMSFT"));
    assertTrue(snapshot.contains("7110\t51499241\t501\tNATGAS\tNATGAS"));
    assertTrue(snapshot.contains("BUY\tRESULT_ONLY\t0.01000000"));
    assertTrue(snapshot.contains("9405\t1\tTreasury 2026\tBOND\tPLN"));
    assertTrue(snapshot.contains("9406\t1\tReserve deposit\tDEPOSIT\tPLN"));
    assertTrue(snapshot.contains("9405\t2026-02-28\tPAY_OUT\t0.190000000000"));
    assertTrue(snapshot.contains("9406\t2027-08-01\tCAPITALIZE\t0.040000000000"));
    assertTrue(snapshot.contains("\t159307.015664000000\t970000.000000000000\t74400.000000000000"));
    assertTrue(snapshot.contains("9201\t1\tHappy Investor Plan\t9202"));
    assertTrue(snapshot.contains("2025\tDRAFT\t9201\t9202"));

    assertTrue(common.contains("(9401, 1, 'Cash reserve'"));
    assertTrue(common.contains("(9501, 9402, DATE '2024-08-01'"));
    assertTrue(common.contains("(9201, 1, 'Happy Investor Plan'"));
    assertTrue(broker.contains("(7108, 17959259, 451, 'MSFT.US', 'MSFT', 'BUY', 'CASH_SETTLED'"));
    assertTrue(broker.contains("(7110, 51499241, 501, 'NATGAS', 'NATGAS', 'BUY', 'RESULT_ONLY'"));
    assertTrue(broker.contains("'CLOSE_TRADE', 501, 'NATGAS'"));
    assertTrue(broker.contains("'EUR', 'PLN', 4.2952983671"));
    assertTrue(broker.contains("17181.1934684000, 'PLN'"));
    assertTrue(broker.contains("'PLN', 'USD', 0.2519589810778805"));
    assertTrue(broker.contains("125.9794905389403, 'USD'"));
    assertTrue(
        common.contains(
            "2025, 50000, "
                + HappyInvestorPlanFacts.BASELINE_INVESTMENT_CAPITAL.toPlainString()
                + ", "
                + HappyInvestorPlanFacts.BASELINE_LONG_TERM_CAPITAL.toPlainString()
                + ", "
                + HappyInvestorLongTermFacts.RENTAL_TOTAL_GROSS_ANNUAL.toPlainString()
                + ", "
                + HappyInvestorLongTermFacts.RENTAL_TOTAL_GROSS_ANNUAL.toPlainString()
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
