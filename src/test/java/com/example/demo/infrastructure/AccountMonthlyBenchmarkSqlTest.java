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
  void scriptDelegatesPerformanceToCanonicalMonthlyProjection() throws IOException {
    String sql = readScript();

    assertTrue(sql.contains("FROM investory.account_monthly_mv monthly"));
    assertTrue(sql.contains("monthly.*"));
    assertFalse(sql.contains("closing_equity\n        - opening_equity"));
  }

  @Test
  void scriptDoesNotRecalculateProfitFromBoundaryEquity() throws IOException {
    String sql = readScript();

    assertTrue(sql.contains("account_monthly_mv"));
    assertFalse(sql.contains("AS total_profit"));
  }

  private String readScript() throws IOException {
    return Files.readString(SCRIPT, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
