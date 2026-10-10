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

    int start =
        migration.indexOf("CREATE VIEW investory.recon_v_account_daily_cashflow_full_precision AS");
    assertTrue(start >= 0, "full-precision C1 view must be present");
    String definition = migration.substring(start, migration.indexOf(';', start));
    String normalized =
        definition.replaceAll("::(?:text|numeric|varchar\\(3\\))", "").replaceAll("[()\\s]+", " ");
    assertTrue(normalized.contains("account.currency AS account_currency"));
    assertTrue(normalized.contains("portfolio.base_currency AS ledger_base_currency"));
    assertTrue(normalized.contains("nco.account_flow_amount_in_portfolio_base_currency"));
    assertTrue(
        normalized.contains(
            "CASE WHEN account.currency = portfolio.base_currency "
                + "THEN ad.cash_balance - COALESCE ad.previous_cash_balance, 0 "
                + "- COALESCE ld.ledger_cash_base, 0 ELSE NULL END AS same_currency_cash_delta_gap"));
  }
}
