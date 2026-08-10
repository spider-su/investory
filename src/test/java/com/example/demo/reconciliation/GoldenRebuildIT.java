package com.example.demo.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.services.PortfolioProjectionService;
import com.example.demo.services.currency.CurrencyRateService;
import com.example.demo.services.imports.ImportExecutionResult;
import com.example.demo.services.imports.ibrk.IbkrImportService;
import com.example.demo.services.imports.xtb.XtbImportV2Service;
import com.investory.testsupport.TestDatabaseFixtures;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Clean-database golden reconciliation built from heavily reduced real broker exports.
 *
 * <p>This test deliberately crosses importer -> normalized cash semantics -> position reconstruction
 * -> account_daily -> independent reconciliation views. It must not call live market-data or FX
 * providers.
 */
@ActiveProfiles("test-fast")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.jpa.hibernate.ddl-auto=validate")
class GoldenRebuildIT {

  private static final String ROOT = "/reconciliation/golden/";
  private static final Set<Long> GOLDEN_ACCOUNTS =
      Set.of(17959259L, 51499241L, 51993106L, 51551301L, 50290466L);

  private static final String CORE_RECON_ACCOUNTS = "17959259,51499241,51993106,51551301,50290466";

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("investory_golden")
          .withUsername("investory")
          .withPassword("investory");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private IbkrImportService ibkrImportService;
  @Autowired private XtbImportV2Service xtbImportV2Service;
  @Autowired private PortfolioProjectionService portfolioProjectionService;
  @Autowired private CurrencyRateService currencyRateService;

  @BeforeAll
  static void migrateFreshDatabase() {
    POSTGRES.start();
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:sql/migration")
        .load()
        .migrate();
    TestDatabaseFixtures.loadPersonalBootstrap(POSTGRES);
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void manifestMatchesEveryGoldenFixture() throws Exception {
    String manifest = new String(resource("manifest.json").readAllBytes(), StandardCharsets.UTF_8);
    Matcher matcher = Pattern.compile(
            "\\\"path\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]+)\\\"\\s*,\\s*\\\"size_bytes\\\"\\s*:\\s*(\\d+)")
        .matcher(manifest);
    int entries = 0;
    while (matcher.find()) {
      entries++;
      byte[] content;
      try (InputStream input = resource(matcher.group(1))) {
        content = input.readAllBytes();
      }
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder actualHash = new StringBuilder();
      for (byte value : digest) actualHash.append(String.format("%02x", value));
      assertEquals(matcher.group(2), actualHash.toString(), matcher.group(1));
      assertEquals(Long.parseLong(matcher.group(3)), content.length, matcher.group(1));
    }
    assertEquals(6, entries);
  }

  @Test
  void rebuildsReducedRealCorpusAndPassesGoldenContracts() throws Exception {
    loadDeterministicFxFixture();
    importIbkrFixture();
    importXtbFixture();

    // Importers may add deterministic execution-rate observations. Rebuild the local cache after all
    // imports, then project only the fixture accounts. recalculateAll() is intentionally not used:
    // it invokes market-data gap filling, which would make this test network/environment dependent.
    currencyRateService.preloadExchangeRates();
    portfolioProjectionService.recalculateAccounts(GOLDEN_ACCOUNTS);
    portfolioProjectionService.refreshReconciliationViews();

    assertNoDuplicateLots();
    assertNoUnclassifiedFixtureCash();
    assertTreasuryLifecycle();
    assertIbkrBusinessDate();
    assertSubaccountRebookingIsPerformanceNeutral();
    assertTrackedAccountTransferIsPerformanceFlowButPortfolioNeutral();
    assertCashOnlyFundingAndIkeAllocation();
    assertResultOnlyCfd();
    assertImportedMoneyAndDerivedDataAreReady();
    assertCoreIndependentReconciliation();

    System.out.println("GOLDEN REBUILD: READY");
  }

  private void importIbkrFixture() throws Exception {
    try (InputStream input = resource("ibkr/U17959259.TRANSACTIONS.GOLDEN.csv")) {
      ImportExecutionResult result =
          ibkrImportService.importStatement(input, "U17959259.TRANSACTIONS.GOLDEN.csv");
      assertEquals(19, result.rowsTotal(), result.details());
      assertEquals(19, result.rowsApplied(), result.details());
      assertEquals(0, result.rowsFailed(), result.details());
    }
  }

  private void importXtbFixture() throws Exception {
    try (InputStream input = resource("xtb/investory_xtb_golden.zip")) {
      ImportExecutionResult result =
          xtbImportV2Service.importZip(input, "investory_xtb_golden.zip");
      assertEquals(0, result.rowsFailed(), result.details());
      assertTrue(result.rowsApplied() > 0, result.details());
    }
  }

  private void loadDeterministicFxFixture() throws IOException {
    jdbc.update("delete from investory.exchange_rates where source = 'TEST'");

    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource("reference/exchange_rates.csv"), StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      assertNotNull(header);
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) {
          continue;
        }
        String[] column = line.split(",", -1);
        if (column.length < 5) {
          throw new IllegalStateException("Malformed golden FX row: " + line);
        }
        LocalDate date = LocalDate.parse(column[0]);
        String base = column[1];
        String target = column[2];
        BigDecimal rate = new BigDecimal(column[3]);
        String reference = "GOLDEN:" + date + ":" + base + ":" + target;

        jdbc.update(
            """
            insert into investory.exchange_rates(
                rate_date, base, to_currency, rate,
                source, method, source_reference
            )
            select day::date, ?, ?, ?, 'TEST', 'MARKET_DAILY', ? || ':' || day::date
            from generate_series(
                ?,
                (date_trunc('month', ?::date + interval '1 month') - interval '1 day')::date,
                interval '3 days'
            ) day
            on conflict do nothing
            """,
            base,
            target,
            rate,
            reference,
            date,
            date);
      }
    }
  }

  private void assertTreasuryLifecycle() {
    Long assetId =
        jdbc.queryForObject(
            """
            select id
            from investory.assets
            where symbol = 'US91282CKB62'
              and asset_type = 'BOND'
            """,
            Long.class);
    assertNotNull(assetId);

    Double acquiredFace =
        jdbc.queryForObject(
            """
            select coalesce(sum(volume), 0)::double precision
            from investory.positions
            where account_id = 17959259
              and asset_id = ?
              and operation = 'BUY'
            """,
            Double.class,
            assetId);
    assertClose(10_000.0, acquiredFace, 0.000001, "Treasury acquired face");

    Double openFace =
        jdbc.queryForObject(
            """
            select coalesce(sum(volume), 0)::double precision
            from investory.positions
            where account_id = 17959259
              and asset_id = ?
              and close_time is null
            """,
            Double.class,
            assetId);
    assertClose(0.0, openFace, 0.000001, "Treasury open face after redemption");

    Map<String, Object> valuation =
        jdbc.queryForMap(
            """
            select
                selected_price::double precision as selected_price,
                contract_multiplier::double precision as contract_multiplier,
                reconstructed_market_value_base::double precision as market_value
            from investory.v_reconstructed_position_daily
            where account_id = 17959259
              and asset_id = ?
              and valuation_date = date '2026-02-26'
            """,
            assetId);
    assertClose(100.42611625, number(valuation.get("selected_price")), 0.000001, "bond price");
    assertClose(0.01, number(valuation.get("contract_multiplier")), 0.000000001, "bond multiplier");
    assertClose(10_042.611625, number(valuation.get("market_value")), 0.01, "bond market value");

    Map<String, Object> redemption =
        jdbc.queryForMap(
            """
            select
                normalized_category,
                performance_flow_amount::double precision as performance_flow,
                portfolio_flow_amount::double precision as portfolio_flow,
                amount::double precision as amount,
                date::date as business_date
            from investory.normalized_cash_operation_flows
            where account_id = 17959259
              and normalized_category = 'BOND_REDEMPTION'
            """);
    assertEquals("BOND_REDEMPTION", redemption.get("normalized_category"));
    assertClose(10_000.0, number(redemption.get("amount")), 0.000001, "redemption principal");
    assertClose(0.0, number(redemption.get("performance_flow")), 0.000001, "redemption performance flow");
    assertClose(0.0, number(redemption.get("portfolio_flow")), 0.000001, "redemption portfolio flow");
    assertEquals(
        LocalDate.of(2026, 2, 27),
        ((java.sql.Date) redemption.get("business_date")).toLocalDate());

    Double coupon =
        jdbc.queryForObject(
            """
            select coalesce(sum(amount), 0)::double precision
            from investory.cash_operations
            where account_id = 17959259
              and operation = 'FREE_FUNDS_INTEREST'
              and comment ilike '%Bond Coupon Payment%'
              and amount > 0
            """,
            Double.class);
    assertTrue(coupon >= 231.25, "Treasury coupon must remain interest income");
  }

  private void assertIbkrBusinessDate() {
    LocalDate date =
        jdbc.queryForObject(
            """
            select date::date
            from investory.cash_operations
            where account_id = 17959259
              and amount = 8793
            """,
            LocalDate.class);
    assertEquals(LocalDate.of(2026, 5, 7), date);

    Integer wrongDayRows =
        jdbc.queryForObject(
            """
            select count(*)
            from investory.cash_operations
            where account_id = 17959259
              and amount = 8793
              and date::date = date '2026-05-06'
            """,
            Integer.class);
    assertEquals(0, wrongDayRows);
  }

  private void assertSubaccountRebookingIsPerformanceNeutral() {
    Map<String, Object> row =
        jdbc.queryForMap(
            """
            select
                sum(amount)::double precision as cash_amount,
                sum(performance_flow_amount)::double precision as performance_flow,
                sum(portfolio_flow_amount)::double precision as portfolio_flow
            from investory.normalized_cash_operation_flows
            where account_id = 51993106
              and raw_operation = 'SUBACCOUNT_TRANSFER'
              and abs(amount) = 6044.12
            """);
    assertClose(0.0, number(row.get("cash_amount")), 0.000001, "rebooking net cash");
    assertClose(0.0, number(row.get("performance_flow")), 0.000001, "rebooking performance flow");
    assertClose(0.0, number(row.get("portfolio_flow")), 0.000001, "rebooking portfolio flow");
  }

  private void assertTrackedAccountTransferIsPerformanceFlowButPortfolioNeutral() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            select
                account_id,
                sum(performance_flow_amount)::double precision as performance_flow,
                sum(portfolio_flow_amount)::double precision as portfolio_flow
            from investory.normalized_cash_operation_flows
            where comment = 'Transfer from 51993106 to 51499241'
            group by account_id
            order by account_id
            """);
    assertEquals(2, rows.size(), rows.toString());

    Map<String, Object> target = rowForAccount(rows, 51499241L);
    Map<String, Object> source = rowForAccount(rows, 51993106L);
    assertClose(325.0, number(target.get("performance_flow")), 0.000001, "target performance flow");
    assertClose(-325.0, number(source.get("performance_flow")), 0.000001, "source performance flow");
    assertClose(0.0, number(target.get("portfolio_flow")), 0.000001, "target portfolio flow");
    assertClose(0.0, number(source.get("portfolio_flow")), 0.000001, "source portfolio flow");
  }

  private void assertCashOnlyFundingAndIkeAllocation() {
    Boolean cashOnly =
        jdbc.queryForObject(
            "select cash_only from investory.accounts where id = 50290466", Boolean.class);
    assertEquals(Boolean.TRUE, cashOnly);

    Double externalFunding =
        jdbc.queryForObject(
            """
            select coalesce(sum(portfolio_flow_amount), 0)::double precision
            from investory.normalized_cash_operation_flows
            where account_id = 50290466
              and normalized_category = 'EXTERNAL_DEPOSIT'
              and amount = 14200
            """,
            Double.class);
    assertClose(14_200.0, externalFunding, 0.000001, "cash-only external funding");

    Double ikeAllocationPortfolioFlow =
        jdbc.queryForObject(
            """
            select coalesce(sum(portfolio_flow_amount), 0)::double precision
            from investory.normalized_cash_operation_flows
            where account_id in (50290466, 51551301)
              and normalized_category in ('INTERNAL_TRANSFER_IN', 'INTERNAL_TRANSFER_OUT')
              and abs(amount) = 14200
            """,
            Double.class);
    assertClose(0.0, ikeAllocationPortfolioFlow, 0.000001, "IKE allocation portfolio flow");
  }

  private void assertResultOnlyCfd() {
    Map<String, Object> position =
        jdbc.queryForMap(
            """
            select
                settlement_model::text as settlement_model,
                profit::double precision as profit,
                swap::double precision as swap
            from investory.positions
            where account_id = 51499241
              and source_position_id = '2040572606'
              and close_time is not null
            """);
    assertEquals("RESULT_ONLY", position.get("settlement_model"));
    assertClose(19.12, number(position.get("profit")), 0.000001, "NATGAS net position result");
    assertClose(-0.68, number(position.get("swap")), 0.000001, "NATGAS position swap");

    Map<String, Object> settlementCash =
        jdbc.queryForMap(
            """
            select
                coalesce(sum(amount) filter (where operation = 'CLOSE_TRADE'), 0)::double precision
                    as close_trade,
                coalesce(sum(amount) filter (where operation = 'ROLLOVER'), 0)::double precision
                    as rollover,
                coalesce(sum(amount) filter (where operation = 'SWAP'), 0)::double precision
                    as cash_swap
            from investory.cash_operations
            where account_id = 51499241
              and comment like '%2040572606%'
            """);
    assertClose(105.90, number(settlementCash.get("close_trade")), 0.000001, "NATGAS close trade cash");
    assertClose(-86.10, number(settlementCash.get("rollover")), 0.000001, "NATGAS rollover cash");
    assertClose(-0.68, number(settlementCash.get("cash_swap")), 0.000001, "NATGAS swap cash");

    Double reconstructed =
        jdbc.queryForObject(
            """
            select reconstructed_total_realized_result::double precision
            from investory.v_realized_result_reconciliation
            where account_id = 51499241
              and valuation_date = date '2025-09-26'
            """,
            Double.class);
    assertClose(19.12, reconstructed, 0.01, "NATGAS independently reconstructed result");
  }

  private void assertCoreIndependentReconciliation() {
    assertNoRows(
        "account_daily independent reconstruction",
        """
        select *
        from investory.v_account_daily_reconciliation
        where account_id in (%s)
          and status = 'FAIL'
        order by abs(equity_difference) desc nulls last, valuation_date
        limit 20
        """
            .formatted(CORE_RECON_ACCOUNTS));

    assertNoRows(
        "position valuation input blockers",
        """
        select *
        from investory.v_position_valuation_validation
        where account_id in (%s)
          and severity = 'ERROR'
        order by valuation_date, account_id, asset_id
        limit 20
        """
            .formatted(CORE_RECON_ACCOUNTS));

    assertNoRows(
        "incomplete realized-result reconstruction",
        """
        select *
        from investory.v_realized_result_reconciliation
        where account_id in (%s)
          and is_complete = false
        order by valuation_date, account_id
        limit 20
        """
            .formatted(CORE_RECON_ACCOUNTS));
  }

  private void assertImportedMoneyAndDerivedDataAreReady() {
    assertNoRows(
        "cash operation currency blockers",
        "select id from investory.cash_operations where currency is null limit 20");
    assertNoRows(
        "position currency blockers",
        """
        select id
        from investory.positions
        where account_id in (%s)
          and (price_currency is null or cost_currency is null or profit_currency is null
               or commission_currency is null)
        limit 20
        """.formatted(CORE_RECON_ACCOUNTS));
    for (String view :
        List.of(
            "account_monthly_mv",
            "portfolio_monthly_mv",
            "account_statistics",
            "portfolio_currency_breakdown",
            "portfolio_asset_allocation",
            "symbol_performance",
            "portfolio_kpi_summary")) {
      Integer rows = jdbc.queryForObject("select count(*) from investory." + view, Integer.class);
      assertNotNull(rows, view + " refresh result missing");
    }
  }

  private void assertNoDuplicateLots() {
    assertNoRows(
        "duplicate position lots",
        "select * from investory.reporting_position_lot_duplicates limit 20");
  }

  private void assertNoUnclassifiedFixtureCash() {
    assertNoRows(
        "unclassified fixture cash",
        """
        select account_id, operation_id, raw_operation, amount, comment, date
        from investory.normalized_cash_operations
        where account_id in (17959259,51499241,51993106,51551301,50290466)
          and normalized_category = 'UNCLASSIFIED'
        order by account_id, date, operation_id
        limit 20
        """);
  }

  private void assertNoRows(String label, String sql) {
    List<Map<String, Object>> rows = jdbc.queryForList(sql);
    assertTrue(rows.isEmpty(), () -> label + " failed:\n" + rows);
  }

  private static Map<String, Object> rowForAccount(List<Map<String, Object>> rows, long accountId) {
    return rows.stream()
        .filter(row -> ((Number) row.get("account_id")).longValue() == accountId)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing transfer row for account " + accountId));
  }

  private static double number(Object value) {
    if (value == null) {
      return 0.0;
    }
    return ((Number) value).doubleValue();
  }

  private static void assertClose(double expected, Double actual, double tolerance, String label) {
    assertNotNull(actual, label + " is null");
    assertEquals(expected, actual, tolerance, label);
  }

  private InputStream resource(String relativePath) {
    InputStream input = getClass().getResourceAsStream(ROOT + relativePath);
    if (input == null) {
      throw new IllegalStateException("Missing golden fixture: " + ROOT + relativePath);
    }
    return input;
  }
}
