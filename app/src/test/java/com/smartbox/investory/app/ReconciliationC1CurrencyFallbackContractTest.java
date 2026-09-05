package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReconciliationC1CurrencyFallbackContractTest {
  @Test
  void c1UsesStableAccountAndPortfolioCurrenciesWhenLedgerDayIsMissing() throws IOException {
    String migration =
        Files.readString(
            Path.of(
                "src",
                "main",
                "resources",
                "sql",
                "migration",
                "V01.006__reconciliation_views.sql"),
            StandardCharsets.UTF_8);

    assertTrue(migration.contains("account.currency::varchar(3) AS account_currency"));
    assertTrue(migration.contains("portfolio.base_currency::varchar(3) AS ledger_base_currency"));
    assertTrue(migration.contains("nco.account_flow_amount_in_portfolio_base_currency"));
    assertTrue(
        migration.contains(
            "CASE WHEN account.currency = portfolio.base_currency\n"
                + "            THEN (ad.cash_balance - COALESCE(ad.previous_cash_balance, 0))\n"
                + "                 - COALESCE(ld.ledger_cash_base, 0)"));
    assertTrue(migration.contains("ELSE NULL::numeric"));
  }
}
