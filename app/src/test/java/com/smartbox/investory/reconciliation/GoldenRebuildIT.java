package com.smartbox.investory.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.investment.imports.ImportExecutionResult;
import com.smartbox.investory.investment.imports.ImportPortfolioContext;
import com.smartbox.investory.investment.imports.ibkr.IbkrImportService;
import com.smartbox.investory.investment.imports.xtb.XtbImportService;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.testsupport.SharedPostgres;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Clean-database golden reconciliation built from reduced, anonymized broker-derived fixtures.
 *
 * <p>This test deliberately crosses importer -> normalized cash semantics -> position
 * reconstruction -> account_daily -> independent reconciliation views. It must not call live
 * market-data or FX providers.
 */
@ActiveProfiles("test-fast")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.jpa.hibernate.ddl-auto=validate")
@DisplayName("Golden Rebuild")
class GoldenRebuildIT {

  private static final String ROOT = "/reconciliation/golden/";
  private static final Set<Long> GOLDEN_ACCOUNTS =
      Set.of(
          HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
          HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
          2051993106L,
          HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
          2050290466L);

  // The two additional XTB accounts belong to the reduced reconciliation corpus, not the
  // four-account canonical Happy Investor profile.
  private static final String CORE_RECON_ACCOUNTS =
      "%d,%d,2051993106,%d,2050290466"
          .formatted(
              HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
              HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
              HappyInvestorTestData.XTB_PLN_ACCOUNT_ID);
  private static final Set<String> EXPECTED_MANIFEST_PATHS =
      Set.of(
          "README.md",
          "expected/checkpoints.json",
          "expected/provenance.json",
          "ibkr/U17959259.TRANSACTIONS.GOLDEN.csv",
          "reference/exchange_rates.csv",
          "xtb/investory_xtb_golden.zip");
  private static final Set<String> EXPECTED_CHECKPOINTS =
      Set.of(
          "IBKR_TREASURY_LIFECYCLE",
          "IBKR_BUSINESS_DATE",
          "IBKR_CROSS_CURRENCY",
          "IBKR_C1_SOURCE_TO_LEDGER",
          "XTB_VHYD_SUBACCOUNT_REBOOK",
          "XTB_TRUE_INTERACCOUNT_TRANSFER",
          "XTB_RESULT_ONLY_CFD",
          "XTB_IKE_CROSS_CURRENCY",
          "XTB_CASH_ONLY_FUNDING");

  private static final WorkerDatabase DATABASE = SharedPostgres.database("golden");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private IbkrImportService ibkrImportService;
  @Autowired private XtbImportService xtbImportService;
  @Autowired private PortfolioProjectionService portfolioProjectionService;
  @Autowired private PortfolioProjectionRefreshService portfolioProjectionRefreshService;
  @Autowired private CurrencyRateService currencyRateService;
  private final GoldenReadinessReport readiness = new GoldenReadinessReport();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private JsonNode checkpoints;

  @BeforeAll
  static void migrateFreshDatabase() {
    Flyway.configure()
        .dataSource(DATABASE.jdbcUrl(), DATABASE.username(), DATABASE.password())
        .schemas("investory")
        .defaultSchema("investory")
        .createSchemas(true)
        .locations("classpath:sql/migration")
        .load()
        .migrate();
  }

  @AfterAll
  static void stopDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @DisplayName("manifest Matches Every Golden Fixture")
  @Test
  void manifestMatchesEveryGoldenFixture() throws Exception {
    String manifest = new String(resource("manifest.json").readAllBytes(), StandardCharsets.UTF_8);
    Matcher matcher =
        Pattern.compile(
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
    assertEquals(EXPECTED_MANIFEST_PATHS.size(), entries);
    assertEquals(EXPECTED_MANIFEST_PATHS, manifestPaths(manifest));
  }

  @DisplayName("rebuilds Reduced Real Corpus And Passes Golden Contracts")
  @Test
  void rebuildsReducedRealCorpusAndPassesGoldenContracts() throws Exception {
    runCheck("checkpoint-contract", "expected/checkpoints.json", this::loadCheckpointContract);
    runCheck("local-fx-fixture", "reference/exchange_rates.csv", this::loadDeterministicFxFixture);
    runCheck(
        "canonical-initial-data", "HappyInvestor portfolio 2", this::assertCanonicalImportScope);
    runCheck("ibkr-import", "ibkr/U17959259.TRANSACTIONS.GOLDEN.csv", this::importIbkrFixture);
    runCheck("xtb-import", "xtb/investory_xtb_golden.zip", this::importXtbFixture);
    runCheck("source-statistics", "imported fixture tables", this::analyzeImportedSources);
    runCheck("complete-fx-coverage", "all fixture valuation dates", this::completeGoldenFxCoverage);

    // Projection reads normalized cash operations. Refresh only that prerequisite before
    // projection; the full reporting refresh belongs after account_daily has been rebuilt.
    runCheck(
        "projection-prerequisites",
        "normalized cash operations",
        () ->
            portfolioProjectionRefreshService.refreshApplicationViews(
                PortfolioProjectionRefreshService.ApplicationRefreshScope
                    .PROJECTION_PREREQUISITES));
    runCheck("cash-fx-readiness", "normalized cash operations", this::assertNormalizedCashFxReady);

    // Importers may add deterministic execution-rate observations. Rebuild the local cache after
    // all
    // imports, then project only the fixture accounts. recalculateAll() is intentionally not used:
    // it invokes market-data gap filling, which would make this test network/environment dependent.
    runCheck(
        "projection-refresh",
        "account_daily and reporting materialized views",
        () -> {
          currencyRateService.clearValuationResolutionCache();
          portfolioProjectionService.recalculateAccounts(GOLDEN_ACCOUNTS);
          // account_daily supplies the valuation dates used by these two dependent MVs. Use the
          // normal timed/ordered refresh path; it sets JIT off and keeps both expensive steps
          // visible in the logs without refreshing unrelated reporting views.
          portfolioProjectionRefreshService.refreshApplicationViews(
              PortfolioProjectionRefreshService.ApplicationRefreshScope.PROJECTION_DEPENDENCIES);
          portfolioProjectionService.refreshReconciliationViews();
        });

    runCheck("duplicate-lots", "positions", this::assertNoDuplicateLots);
    runCheck(
        "classified-cash", "normalized_cash_operations", this::assertNoUnclassifiedFixtureCash);
    runCheck(
        "ibkr-treasury-lifecycle",
        "IBKR account " + HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
        this::assertTreasuryLifecycle);
    runCheck(
        "ibkr-business-date",
        "IBKR account " + HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
        this::assertIbkrBusinessDate);
    runCheck(
        "ibkr-c1-source-to-ledger",
        "IBKR account " + HappyInvestorTestData.IBKR_USD_ACCOUNT_ID + ": operation/currency/date",
        this::assertIbkrSourceToLedger);
    runCheck(
        "xtb-vhyd-rebooking",
        "XTB account 2051993106",
        this::assertSubaccountRebookingIsPerformanceNeutral);
    runCheck(
        "xtb-interaccount-transfer",
        "XTB accounts 2051993106/" + HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
        this::assertTrackedAccountTransferIsPerformanceFlowButPortfolioNeutral);
    runCheck(
        "xtb-cash-only-funding",
        "XTB accounts 2050290466/" + HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
        this::assertCashOnlyFundingAndIkeAllocation);
    runCheck(
        "xtb-result-only-cfd",
        "XTB account " + HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
        this::assertResultOnlyCfd);
    runCheck(
        "derived-data-ready",
        "reporting materialized views",
        this::assertImportedMoneyAndDerivedDataAreReady);
    runCheck(
        "independent-reconciliation",
        "reconciliation views",
        this::assertCoreIndependentReconciliation);
    runCheck(
        "reconciliation-error-scan",
        "all reconciliation materialized views",
        this::assertNoReconciliationErrors);

    String summary = readiness.summary();
    Files.createDirectories(Path.of("target"));
    Files.writeString(
        Path.of("target/golden-readiness.json"), readiness.json(), StandardCharsets.UTF_8);
    System.out.println(summary);
    if (!readiness.ready()) {
      fail(readiness.summary());
    }
  }

  private void runCheck(String checkId, String scope, ThrowingCheck check) {
    try {
      check.run();
      readiness.pass(checkId, scope, "passed");
    } catch (Throwable failure) {
      readiness.fail(checkId, scope, failure);
    }
  }

  private Set<String> manifestPaths(String manifest) {
    Set<String> paths = new HashSet<>();
    Matcher matcher = Pattern.compile("\\\"path\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(manifest);
    while (matcher.find()) {
      paths.add(matcher.group(1));
    }
    return paths;
  }

  private void loadCheckpointContract() throws Exception {
    checkpoints = objectMapper.readTree(resource("expected/checkpoints.json"));
    Set<String> actualIds = new HashSet<>();
    JsonNode cases = checkpoints.path("cases");
    assertTrue(cases.isArray() && !cases.isEmpty(), "checkpoint cases must be a non-empty array");
    for (JsonNode checkpoint : cases) {
      String id = checkpoint.path("id").asText("");
      assertTrue(actualIds.add(id), "duplicate checkpoint id: " + id);
      assertTrue(checkpoint.path("assertions").isObject(), "missing assertions for " + id);
      assertTrue(checkpoint.path("assertions").size() > 0, "empty assertions for " + id);
    }
    assertEquals(EXPECTED_CHECKPOINTS, actualIds);
    assertEquals(1, checkpoints.path("version").asInt(), "unsupported checkpoint version");
  }

  @FunctionalInterface
  private interface ThrowingCheck {
    void run() throws Exception;
  }

  private void importIbkrFixture() throws Exception {
    try (ImportPortfolioContext.Scope ignored =
            ImportPortfolioContext.open(HappyInvestorTestData.PORTFOLIO_ID);
        InputStream input = resource("ibkr/U17959259.TRANSACTIONS.GOLDEN.csv")) {
      ImportExecutionResult result =
          ibkrImportService.importStatement(input, "U17959259.TRANSACTIONS.GOLDEN.csv");
      assertEquals(19, result.rowsTotal(), result.details());
      assertEquals(19, result.rowsApplied(), result.details());
      assertEquals(0, result.rowsFailed(), result.details());
    }
  }

  private void assertCanonicalImportScope() {
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from investory.app_users where id = 2 and username = 'happy.investor'",
            Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            select count(*) from investory.portfolios
            where id = 2 and user_id = 2 and base_currency = 'PLN' and local_currency = 'PLN'
            """,
            Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            """
            select count(*) from investory.accounts
            where id = 2017959259 and portfolio_id = 2 and provider = 'IBKR'
              and external_account_id = '17959259'
            """,
            Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from investory.accounts where portfolio_id = 2 and provider = 'IBKR' and external_account_id = '17959259'",
            Integer.class));
  }

  private void importXtbFixture() throws Exception {
    try (ImportPortfolioContext.Scope ignored =
            ImportPortfolioContext.open(HappyInvestorTestData.PORTFOLIO_ID);
        InputStream input = resource("xtb/investory_xtb_golden.zip")) {
      ImportExecutionResult result = xtbImportService.importZip(input, "investory_xtb_golden.zip");
      assertEquals(0, result.rowsFailed(), result.details());
      assertTrue(result.rowsApplied() > 0, result.details());
    }
  }

  private void analyzeImportedSources() {
    jdbc.execute(
        "ANALYZE investory.exchange_rates, investory.cash_operations, "
            + "investory.positions, investory.asset_price_history, investory.account_daily");
  }

  private void loadDeterministicFxFixture() throws IOException {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                resource("reference/exchange_rates.csv"), StandardCharsets.UTF_8))) {
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
        BigDecimal actual =
            jdbc.queryForObject(
                """
                select rate from investory.fx_daily_rates
                where rate_date = ? and base = ? and to_currency = ?
                """,
                BigDecimal.class,
                date,
                base,
                target);
        assertNotNull(actual, "missing initial FX " + date + " " + base + "->" + target);
        assertEquals(
            0,
            rate.setScale(8, RoundingMode.HALF_UP).compareTo(actual),
            "initial FX " + date + " " + base + "->" + target);
      }
    }
    currencyRateService.clearValuationResolutionCache();
  }

  private void extendDeterministicFxThroughCurrentDate() {
    jdbc.update(
        """
        with latest as (
            select distinct on (base, to_currency)
                   rate_date, base, to_currency, rate
            from investory.fx_daily_rates
            where source = 'TEST'
            order by base, to_currency, rate_date desc, id desc
        )
        insert into investory.fx_daily_rates(
            rate_date, base, to_currency, rate,
            source, method, source_rate_date, source_reference
        )
        select day::date, latest.base, latest.to_currency, latest.rate,
               'TEST', 'CARRY_FORWARD', latest.rate_date,
               'GOLDEN:current-coverage:' || latest.base || ':' || latest.to_currency || ':' || day::date
        from latest
        cross join lateral generate_series(
            latest.rate_date + 1,
            current_date,
            interval '1 day'
        ) day
        on conflict do nothing
        """);
  }

  private void deriveMissingGoldenFxDirections() {
    jdbc.update(
        """
        insert into investory.fx_daily_rates(
            rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
        select rate_date, 'USD', 'EUR', 1 / rate, 'TEST', 'OBSERVED', source_rate_date,
               source_reference || ':RECIPROCAL'
        from investory.fx_daily_rates
        where source = 'TEST' and base = 'EUR' and to_currency = 'USD'
        on conflict do nothing
        """);
    jdbc.update(
        """
        insert into investory.fx_daily_rates(
            rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
        select rate_date, 'PLN', 'EUR', 1 / rate, 'TEST', 'OBSERVED', source_rate_date,
               source_reference || ':RECIPROCAL'
        from investory.fx_daily_rates
        where source = 'TEST' and base = 'EUR' and to_currency = 'PLN'
        on conflict do nothing
        """);
    jdbc.update(
        """
        insert into investory.fx_daily_rates(
            rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
        select day::date, pairs.base, pairs.to_currency, latest.rate, 'TEST', 'CARRY_FORWARD',
               latest.source_rate_date,
               'GOLDEN:complete-coverage:' || pairs.base || ':' || pairs.to_currency || ':' || day::date
        from generate_series(
                 (select min(rate_date) from investory.fx_daily_rates where source = 'TEST'),
                 current_date, interval '1 day') day
        cross join (values ('EUR'::varchar(3), 'USD'::varchar(3)),
                           ('USD'::varchar(3), 'EUR'::varchar(3)),
                           ('EUR'::varchar(3), 'PLN'::varchar(3)),
                           ('PLN'::varchar(3), 'EUR'::varchar(3)),
                           ('USD'::varchar(3), 'PLN'::varchar(3)),
                           ('PLN'::varchar(3), 'USD'::varchar(3))) pairs(base, to_currency)
        cross join lateral (
            select rate, source_rate_date
            from investory.fx_daily_rates fx
            where fx.source = 'TEST' and fx.base = pairs.base and fx.to_currency = pairs.to_currency
              and fx.rate_date <= day::date
            order by fx.rate_date desc
            limit 1) latest
        on conflict do nothing
        """);
  }

  private void completeGoldenFxCoverage() {
    jdbc.update(
        """
        INSERT INTO investory.fx_daily_rates(
            rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
        SELECT DATE '2026-04-15', base, to_currency, rate, 'TEST', 'OBSERVED', DATE '2026-04-15',
               'GOLDEN:2026-04-15:' || base || ':' || to_currency
        FROM investory.fx_daily_rates
        WHERE rate_date = DATE '2026-04-01' AND source = 'DB60_INITIAL'
          AND base IN ('USD', 'EUR', 'PLN') AND to_currency IN ('USD', 'EUR', 'PLN')
          AND base <> to_currency
        ON CONFLICT (rate_date, base, to_currency) DO UPDATE SET
            rate = EXCLUDED.rate, source = EXCLUDED.source, method = EXCLUDED.method,
            source_rate_date = EXCLUDED.source_rate_date, source_reference = EXCLUDED.source_reference
        """);
    currencyRateService.clearValuationResolutionCache();

    LocalDate knownRequiredDate = LocalDate.of(2026, 4, 15);
    Integer dailyRows =
        jdbc.queryForObject(
            """
            select count(*) from investory.fx_daily_rates
            where rate_date = ? and base = 'USD' and to_currency = 'PLN'
            """,
            Integer.class,
            knownRequiredDate);
    assertEquals(1, dailyRows);
    String conversionStatus =
        jdbc.queryForObject(
            """
            select conversion_status
            from investory.resolve_fx_rate(?, 'USD', 'PLN')
            """,
            String.class,
            knownRequiredDate);
    assertTrue(
        "OK".equals(conversionStatus) || "CARRY_FORWARD".equals(conversionStatus),
        "initial USD->PLN resolver status: " + conversionStatus);
    String portfolioConversionStatus =
        jdbc.queryForObject(
            """
            select conversion_status
            from investory.resolve_portfolio_fx_rate(2, ?, 'USD')
            """,
            String.class,
            knownRequiredDate);
    assertTrue(
        "OK".equals(portfolioConversionStatus) || "CARRY_FORWARD".equals(portfolioConversionStatus),
        "initial portfolio USD->PLN resolver status: " + portfolioConversionStatus);
  }

  private void assertNormalizedCashFxReady() {
    List<Map<String, Object>> missing =
        jdbc.queryForList(
            """
            select nco.operation_id, nco.date, nco.currency, nco.base_currency,
                   nco.portfolio_fx_source, nco.portfolio_source_rate_date,
                   nco.portfolio_conversion_status, nco.account_conversion_status,
                   direct.source as direct_portfolio_fx_source,
                   direct.source_rate_date as direct_portfolio_source_rate_date,
                   direct.conversion_status as direct_portfolio_conversion_status
            from investory.app_v_normalized_cash_operations nco
            cross join lateral investory.resolve_portfolio_fx_rate(
                nco.portfolio_id,
                (nco.date at time zone 'Europe/Warsaw')::date,
                nco.currency) direct
            where not investory.fx_status_usable(portfolio_conversion_status)
               or not investory.fx_status_usable(account_conversion_status)
            order by date, operation_id
            """);
    assertTrue(missing.isEmpty(), "normalized cash operations missing FX: " + missing);
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
                    where account_id = %d
                      and asset_id = ?
                      and operation = 'BUY'
                    """
                .formatted(HappyInvestorTestData.IBKR_USD_ACCOUNT_ID),
            Double.class,
            assetId);
    assertClose(10_000.0, acquiredFace, 0.000001, "Treasury acquired face");

    Double openFace =
        jdbc.queryForObject(
            """
                    select coalesce(sum(volume), 0)::double precision
                    from investory.positions
                    where account_id = 2017959259
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
                        reconstructed_market_value_base::double precision as market_value,
                        fx_rate_to_base::double precision as fx_rate_to_base
                    from investory.app_v_reconstructed_position_daily
                    where account_id = 2017959259
                      and asset_id = ?
                      and valuation_date = date '2026-02-26'
                    """,
            assetId);
    assertClose(100.42611625, number(valuation.get("selected_price")), 0.000001, "bond price");
    assertClose(0.01, number(valuation.get("contract_multiplier")), 0.000000001, "bond multiplier");
    assertClose(
        10_042.611625 * number(valuation.get("fx_rate_to_base")),
        number(valuation.get("market_value")),
        0.01,
        "bond market value in portfolio base currency");

    Map<String, Object> redemption =
        jdbc.queryForMap(
            """
                    select
                        normalized_category,
                        performance_flow_amount::double precision as performance_flow,
                        portfolio_flow_amount::double precision as portfolio_flow,
                        amount::double precision as amount,
                        date::date as business_date
                    from investory.app_v_normalized_cash_operation_flows
                    where account_id = 2017959259
                      and normalized_category = 'BOND_REDEMPTION'
                    """);
    assertEquals("BOND_REDEMPTION", redemption.get("normalized_category"));
    assertClose(10_000.0, number(redemption.get("amount")), 0.000001, "redemption principal");
    assertClose(
        0.0, number(redemption.get("performance_flow")), 0.000001, "redemption performance flow");
    assertClose(
        0.0, number(redemption.get("portfolio_flow")), 0.000001, "redemption portfolio flow");
    assertEquals(
        LocalDate.of(2026, 2, 27), ((java.sql.Date) redemption.get("business_date")).toLocalDate());

    Double coupon =
        jdbc.queryForObject(
            """
                    select coalesce(sum(amount), 0)::double precision
                    from investory.cash_operations
                    where account_id = 2017959259
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
                    where account_id = 2017959259
                      and amount = 8793
                    """,
            LocalDate.class);
    assertEquals(LocalDate.of(2026, 5, 7), date);

    Integer wrongDayRows =
        jdbc.queryForObject(
            """
                    select count(*)
                    from investory.cash_operations
                    where account_id = 2017959259
                      and amount = 8793
                      and date::date = date '2026-05-06'
                    """,
            Integer.class);
    assertEquals(0, wrongDayRows);
  }

  private void assertIbkrSourceToLedger() throws IOException {
    Map<CashDimension, CashTotals> expected = new HashMap<>();
    List<String> lines =
        new BufferedReader(
                new InputStreamReader(
                    resource("ibkr/U17959259.TRANSACTIONS.GOLDEN.csv"), StandardCharsets.UTF_8))
            .lines()
            .toList();
    String baseCurrency = null;
    String[] header = null;
    for (String line : lines) {
      String[] fields = csvFields(line);
      if (fields.length >= 4
          && "Summary".equals(fields[0])
          && "Data".equals(fields[1])
          && "Base Currency".equalsIgnoreCase(fields[2])) {
        baseCurrency = fields[3].trim();
      }
      if (fields.length >= 3
          && "Transaction History".equals(fields[0])
          && "Header".equals(fields[1])) {
        header = fields;
      }
      if (header == null
          || fields.length < header.length
          || !"Transaction History".equals(fields[0])
          || !"Data".equals(fields[1])) {
        continue;
      }
      Map<String, Integer> columns = new HashMap<>();
      for (int i = 0; i < header.length; i++) columns.put(header[i].trim(), i);
      String date = value(fields, columns, "Date");
      String type = value(fields, columns, "Transaction Type");
      String description = value(fields, columns, "Description");
      String net = value(fields, columns, "Net Amount");
      assertNotNull(baseCurrency, "IBKR source base currency");
      assertNotNull(date, "IBKR source date");
      assertNotNull(type, "IBKR source transaction type");
      assertNotNull(net, "IBKR source net amount");
      String operation = ibkrCashOperation(type, description);
      assertTrue(!"UNKNOWN".equals(operation), "unmapped IBKR transaction type: " + type);
      CashDimension key = new CashDimension(operation, baseCurrency, LocalDate.parse(date));
      CashTotals totals = expected.computeIfAbsent(key, ignored -> new CashTotals());
      totals.count++;
      totals.amount = totals.amount.add(new BigDecimal(net.trim()));
    }

    Map<CashDimension, CashTotals> actual = new HashMap<>();
    jdbc.query(
        """
                    select co.operation::text as operation, co.currency, cast(co.date as date) as operation_date,
                           count(*) as row_count, coalesce(sum(co.amount), 0) as amount
            from investory.cash_operations co
            where co.account_id = 2017959259
                    group by co.operation, co.currency, co.date::date
                    """,
        rs -> {
          CashDimension key =
              new CashDimension(
                  rs.getString("operation"),
                  rs.getString("currency"),
                  rs.getDate("operation_date").toLocalDate());
          CashTotals totals = new CashTotals();
          totals.count = rs.getLong("row_count");
          totals.amount = rs.getBigDecimal("amount");
          actual.put(key, totals);
        });

    assertEquals(expected.keySet(), actual.keySet(), "IBKR C1 operation/currency/date dimensions");
    for (CashDimension key : expected.keySet()) {
      CashTotals source = expected.get(key);
      CashTotals ledger = actual.get(key);
      assertEquals(source.count, ledger.count, "IBKR C1 row count for " + key);
      assertTrue(
          source.amount.subtract(ledger.amount).abs().compareTo(new BigDecimal("0.00000001")) <= 0,
          "IBKR C1 amount for " + key + ": source=" + source.amount + ", ledger=" + ledger.amount);
    }
  }

  private static String ibkrCashOperation(String type, String description) {
    String normalized = type.toLowerCase(java.util.Locale.ROOT).trim();
    if (normalized.startsWith("corporate action") || normalized.startsWith("forex trade component"))
      return "TRANSFER";
    return switch (normalized) {
      case "buy" -> "STOCK_PURCHASE";
      case "sell" -> "STOCK_SELL";
      case "dividend" -> "DIVIDEND";
      case "foreign tax withholding" -> "WITHHOLDING_TAX";
      case "credit interest", "investment interest received", "investment interest paid" ->
          "FREE_FUNDS_INTEREST";
      case "deposit" ->
          description != null && "cash transfer".equalsIgnoreCase(description.trim())
              ? "TRANSFER"
              : "DEPOSIT";
      case "withdrawal" -> "WITHDRAWAL";
      case "adjustment" -> "CORRECTION";
      default -> "UNKNOWN";
    };
  }

  private static String value(String[] fields, Map<String, Integer> columns, String name) {
    Integer index = columns.get(name);
    return index == null || index >= fields.length ? null : fields[index];
  }

  private static String[] csvFields(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (ch == ',' && !quoted) {
        fields.add(field.toString());
        field.setLength(0);
      } else {
        field.append(ch);
      }
    }
    fields.add(field.toString());
    return fields.toArray(String[]::new);
  }

  private record CashDimension(String operation, String currency, LocalDate date) {}

  private static final class CashTotals {
    long count;
    BigDecimal amount = BigDecimal.ZERO;
  }

  private void assertSubaccountRebookingIsPerformanceNeutral() {
    Map<String, Object> row =
        jdbc.queryForMap(
            """
                    select
                        sum(amount)::double precision as cash_amount,
                        sum(performance_flow_amount)::double precision as performance_flow,
                        sum(portfolio_flow_amount)::double precision as portfolio_flow
                    from investory.app_v_normalized_cash_operation_flows
                    where account_id = 2051993106
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
                    from investory.app_v_normalized_cash_operation_flows
                    where comment = 'Transfer from 51993106 to 51499241'
                    group by account_id
                    order by account_id
                    """);
    assertEquals(2, rows.size(), rows.toString());

    Map<String, Object> target = rowForAccount(rows, HappyInvestorTestData.XTB_USD_ACCOUNT_ID);
    Map<String, Object> source = rowForAccount(rows, 2051993106L);
    assertClose(325.0, number(target.get("performance_flow")), 0.000001, "target performance flow");
    assertClose(
        -325.0, number(source.get("performance_flow")), 0.000001, "source performance flow");
    assertClose(0.0, number(target.get("portfolio_flow")), 0.000001, "target portfolio flow");
    assertClose(0.0, number(source.get("portfolio_flow")), 0.000001, "source portfolio flow");
  }

  private void assertCashOnlyFundingAndIkeAllocation() {
    Boolean cashOnly =
        jdbc.queryForObject(
            "select cash_only from investory.accounts where id = 2050290466", Boolean.class);
    assertEquals(Boolean.TRUE, cashOnly);

    Double externalFunding =
        jdbc.queryForObject(
            """
                    select coalesce(sum(portfolio_flow_amount), 0)::double precision
                    from investory.app_v_normalized_cash_operation_flows
                    where account_id = 2050290466
                      and normalized_category = 'EXTERNAL_DEPOSIT'
                      and amount = 14200
                    """,
            Double.class);
    assertClose(14_200.0, externalFunding, 0.000001, "cash-only external funding");

    Double ikeAllocationPortfolioFlow =
        jdbc.queryForObject(
            """
                    select coalesce(sum(portfolio_flow_amount), 0)::double precision
                    from investory.app_v_normalized_cash_operation_flows
                    where account_id in (2050290466, 2051551301)
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
                    where account_id = 2051499241
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
                    where account_id = 2051499241
                      and comment like '%2040572606%'
                    """);
    assertClose(
        105.90, number(settlementCash.get("close_trade")), 0.000001, "NATGAS close trade cash");
    assertClose(-86.10, number(settlementCash.get("rollover")), 0.000001, "NATGAS rollover cash");
    assertClose(-0.68, number(settlementCash.get("cash_swap")), 0.000001, "NATGAS swap cash");

    Double reconstructed =
        jdbc.queryForObject(
            """
                    select coalesce(sum(profit), 0)::double precision
                    from investory.positions
                    where account_id = 2051499241
                      and source_position_id = '2040572606'
                      and close_time::date = date '2025-09-26'
                    """,
            Double.class);
    assertClose(19.12, reconstructed, 0.01, "NATGAS independently reconstructed result");
  }

  private void assertCoreIndependentReconciliation() {
    assertNoRows(
        "account_daily independent reconstruction",
        """
            select *
            from investory.recon_v_account_daily
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
            from investory.recon_v_position_valuation_validation
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
            from investory.recon_v_realized_result
            where account_id in (%s)
              and is_complete = false
            order by valuation_date, account_id
            limit 20
            """
            .formatted(CORE_RECON_ACCOUNTS));
  }

  private void assertNoReconciliationErrors() {
    List<String> views =
        List.of(
            "recon_v_account_daily",
            "recon_v_account_daily_cashflow",
            "recon_v_account_monthly_profit",
            "recon_v_account_statistics_vs_daily",
            "recon_v_trade_settlement");
    for (String view : views) {
      Set<String> columns =
          new HashSet<>(
              jdbc.queryForList(
                  "select column_name from information_schema.columns "
                      + "where table_schema = 'investory' and table_name = ?",
                  String.class,
                  view));
      if (columns.contains("status")) {
        Integer failures =
            jdbc.queryForObject(
                "select count(*) from investory."
                    + view
                    + " where status in ('FAIL', 'ERROR', 'MISMATCH')",
                Integer.class);
        assertEquals(0, failures, view + " status failures");
      }
      if (columns.contains("severity")) {
        Integer failures =
            jdbc.queryForObject(
                "select count(*) from investory." + view + " where severity = 'ERROR'",
                Integer.class);
        assertEquals(0, failures, view + " severity errors");
      }
    }
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
            """
            .formatted(CORE_RECON_ACCOUNTS));
    for (String view :
        List.of(
            "app_v_account_monthly",
            "app_v_portfolio_performance_daily",
            "app_v_account_statistics",
            "app_v_portfolio_currency_breakdown",
            "app_v_portfolio_asset_allocation",
            "app_v_symbol_performance",
            "app_v_portfolio_kpi_summary")) {
      Boolean queryable =
          jdbc.queryForObject(
              "select exists(select 1 from investory." + view + " limit 1)", Boolean.class);
      assertNotNull(queryable, view + " refresh result missing");
    }
  }

  private void assertNoDuplicateLots() {
    assertNoRows(
        "duplicate position lots",
        "select * from investory.recon_v_position_lot_duplicates limit 20");
  }

  private void assertNoUnclassifiedFixtureCash() {
    assertNoRows(
        "unclassified fixture cash",
        """
            select account_id, operation_id, raw_operation, amount, comment, date
            from investory.app_v_normalized_cash_operations
            where account_id in (2017959259,2051499241,2051993106,2051551301,2050290466)
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
