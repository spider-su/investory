package com.smartbox.investory.testsupport.happyinvestor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Compares canonical scenario semantics with the persisted SQL overlays. */
class HappyInvestorFixtureParityTest {
  @Test
  void javaScenarioAndPersistedOverlaysTellTheSameCanonicalStory() throws IOException {
    var scenario = HappyInvestorScenario.create();
    String common = resource("db/snapshot/happyinvestor-common.sql");
    String broker = resource("db/snapshot/happyinvestor-broker.sql");

    assertThat(scenario.accounts()).hasSize(HappyInvestorExpected.ACCOUNT_COUNT);
    assertThat(scenario.accounts())
        .extracting(account -> account.getId())
        .containsExactlyInAnyOrder(
            HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
            HappyInvestorTestData.XTB_EUR_ACCOUNT_ID);
    assertThat(scenario.accounts())
        .extracting(account -> account.getCurrency().name())
        .containsExactlyInAnyOrder("USD", "USD", "PLN", "EUR");

    List<String> accountIdentities =
        List.of(
            "91000001, '90000001', 'USD', 'IBKR'",
            "91000002, '90000002', 'USD', 'XTB'",
            "91000003, '90000003', 'PLN', 'XTB'",
            "91000004, '90000009', 'EUR', 'XTB'");
    accountIdentities.forEach(identity -> assertThat(common).contains(identity));

    assertThat(HappyInvestorScenario.externalCashOperations()).hasSize(10);
    for (String operation :
        List.of(
            "7001, 91000001, 'DEPOSIT', 100000, 'USD'",
            "7002, 91000001, 'WITHDRAWAL', -3000, 'USD'",
            "7024, 91000001, 'WITHDRAWAL', -100000, 'USD'",
            "7025, 91000001, 'WITHDRAWAL', -7934.73331300, 'USD'",
            "7003, 91000002, 'DEPOSIT', 4000, 'USD'",
            "7006, 91000003, 'WITHDRAWAL', -1000, 'PLN'",
            "7007, 91000004, 'DEPOSIT', 8000, 'EUR'",
            "7008, 91000004, 'WITHDRAWAL', -2000, 'EUR'")) {
      assertThat(broker).as("persisted operation %s", operation).contains(operation);
    }

    for (String transfer :
        List.of(
            "'EUR', 'USD', 1.082239",
            "'EUR', 'PLN', 4.2952983671",
            "'PLN', 'USD', 0.2519589810778805",
            "'USD', 'PLN', 3.9993")) {
      assertThat(broker).as("persisted FX transfer %s", transfer).contains(transfer);
    }
    assertThat(broker)
        .contains("'AAPL.US'")
        .contains("'MSFT.US'")
        .contains("'VWRA.UK'")
        .contains("'NVDA.US'")
        .contains("'TSLA.US'")
        .contains("'GOOGL.US'")
        .contains("'US91282CKB62'");
    assertThat(
            scenario.openPositions().stream()
                .map(position -> position.getSymbol())
                .distinct()
                .toList())
        .containsExactlyInAnyOrder(
            "AAPL.US", "VWRA.UK", "NVDA.US", "TSLA.US", "GOOGL.US", "MSFT.US", "US91282CRC72");

    assertThat(broker).contains("'NATGAS', 'NATGAS', 'BUY', 'RESULT_ONLY'");
    assertThat(broker).contains("'CLOSE_TRADE', 501, 'NATGAS'").contains("'SWAP', 501, 'NATGAS'");
    assertThat(scenario.closedPositions())
        .anyMatch(position -> "NATGAS".equals(position.getSymbol()));
    assertThat(common).contains("(9201, 2, 'Happy Investor Plan'");
    assertThat(common).contains("2025, 50000, 159307.015664, 970000");
    assertThat(common).contains("'Apartment A'").contains("'Apartment B'").contains("'Family Car'");
  }

  private static String resource(String path) throws IOException {
    try (InputStream stream =
        HappyInvestorFixtureParityTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing canonical resource: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
