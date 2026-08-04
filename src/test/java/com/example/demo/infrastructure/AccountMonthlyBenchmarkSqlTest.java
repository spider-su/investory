package com.example.demo.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AccountMonthlyBenchmarkSqlTest {

  private static final Path SCRIPT =
      Path.of("scripts", "sql", "rebuild_account_monthly_benchmark.sql");

  @Test
  void plnAccountFixtureUsesPortfolioBaseEquityAndExternalFlowsOnly() {
    double openingEquityPln = 40_000.0;
    double closingEquityPln = 44_000.0;
    double externalDepositPln = 4_000.0;
    double plnToUsd = 0.25;

    double openingEquityUsd = openingEquityPln * plnToUsd;
    double closingEquityUsd = closingEquityPln * plnToUsd;
    double externalDepositUsd = externalDepositPln * plnToUsd;

    double profitUsd = closingEquityUsd - openingEquityUsd - externalDepositUsd;
    double returnPct = profitUsd / openingEquityUsd * 100.0;

    assertEquals(10_000.0, openingEquityUsd);
    assertEquals(11_000.0, closingEquityUsd);
    assertEquals(1_000.0, externalDepositUsd);
    assertEquals(0.0, profitUsd);
    assertEquals(0.0, returnPct);
  }

  @Test
  void scriptKeepsEquityAndCashFlowsInPortfolioBaseCurrency() throws IOException {
    String sql = readScript();

    assertTrue(sql.contains("ad.equity * fx.fx_rate_to_base AS equity"));
    assertTrue(sql.contains("p.base_currency::text AS valuation_currency"));
    assertTrue(sql.contains("amount_in_portfolio_base_currency"));
    assertTrue(sql.contains("'EXTERNAL_DEPOSIT'"));
    assertTrue(sql.contains("'EXTERNAL_WITHDRAWAL'"));

    assertFalse(sql.contains("amount_in_account_currency"));
    assertFalse(sql.contains("'FX_CONVERSION'"));
    assertFalse(sql.contains("'INTERNAL_TRANSFER_IN'"));
    assertFalse(sql.contains("'INTERNAL_TRANSFER_OUT'"));
    assertFalse(sql.contains("'INTERNAL_BOOKKEEPING'"));
  }

  @Test
  void scriptCalculatesProfitFromConvertedEquityAndConvertedExternalFlow() throws IOException {
    String sql = readScript();

    assertTrue(
        sql.contains(
            "equity.closing_equity\n        - equity.opening_equity\n        - coalesce(flows.net_external_flow, 0) AS total_profit"));
    assertTrue(sql.contains("JOIN investory.v_reporting_daily_fx_rate fx"));
    assertTrue(sql.contains("fx.from_currency = ad.valuation_currency"));
    assertTrue(sql.contains("fx.valuation_date = ad.snapshot_date"));
  }

  private String readScript() throws IOException {
    return Files.readString(SCRIPT, StandardCharsets.UTF_8);
  }
}
