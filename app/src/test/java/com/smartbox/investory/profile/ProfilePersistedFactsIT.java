package com.smartbox.investory.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorLongTermFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorProfileFacts;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** Verifies the single-transaction whole-wealth composition boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-fast")
@TestPropertySource(properties = "app.portfolio.performance-kpi-start=2024-01-01")
@Import(ProfilePersistedFactsIT.FixedClockConfig.class)
class ProfilePersistedFactsIT {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("profile_persisted_facts");

  @Autowired private ProfileSnapshotReader profiles;
  @Autowired private DataSource dataSource;
  @Autowired private PortfolioProjectionService projections;
  @Autowired private PortfolioProjectionRefreshService projectionRefresh;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private InvestmentDashboardApi investmentDashboard;

  @BeforeEach
  void loadCanonicalHappyInvestorFixture() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-common.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-broker.sql"));
      try (var statement =
          connection.prepareStatement(
              "DELETE FROM investory.positions WHERE open_time::date > ?")) {
        statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
        statement.executeUpdate();
      }
      try (var statement =
          connection.prepareStatement(
              "UPDATE investory.positions SET close_time = NULL WHERE close_time::date > ?")) {
        statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
        statement.executeUpdate();
      }
      try (var statement =
          connection.prepareStatement(
              "DELETE FROM investory.cash_operations WHERE date::date > ?")) {
        statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
        statement.executeUpdate();
      }
      try (var statement = connection.createStatement()) {
        statement.execute("REFRESH MATERIALIZED VIEW investory.app_v_normalized_cash_operations");
      }
      seedCurrentValuationFx(connection);
    }
    projections.recalculateAccounts(
        Set.of(
            HappyInvestorTestData.IBKR_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_USD_ACCOUNT_ID,
            HappyInvestorTestData.XTB_PLN_ACCOUNT_ID,
            HappyInvestorTestData.XTB_EUR_ACCOUNT_ID));
    projectionRefresh.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.DASHBOARD);
  }

  private static void seedCurrentValuationFx(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            """
            INSERT INTO investory.exchange_rates(
                rate_date, base, to_currency, rate, source, method, source_rate_date, source_reference)
            WITH anchors AS (
              SELECT
                (SELECT rate FROM investory.exchange_rates WHERE rate_date = ? AND base = 'USD' AND to_currency = 'PLN' ORDER BY id DESC LIMIT 1) AS usd_pln,
                (SELECT rate FROM investory.exchange_rates WHERE rate_date = ? AND base = 'EUR' AND to_currency = 'USD' ORDER BY id DESC LIMIT 1) AS eur_usd
            ), matrix(source_currency, target_currency, rate) AS (
              SELECT 'USD', 'PLN', usd_pln FROM anchors
              UNION ALL SELECT 'PLN', 'USD', 1 / usd_pln FROM anchors
              UNION ALL SELECT 'EUR', 'USD', eur_usd FROM anchors
              UNION ALL SELECT 'USD', 'EUR', 1 / eur_usd FROM anchors
              UNION ALL SELECT 'EUR', 'PLN', eur_usd * usd_pln FROM anchors
              UNION ALL SELECT 'PLN', 'EUR', 1 / (eur_usd * usd_pln) FROM anchors
            )
            SELECT CURRENT_DATE, source_currency, target_currency, rate,
                   'TEST', 'OBSERVED', ?, 'HAPPYINVESTOR_REFERENCE'
            FROM matrix
            ON CONFLICT (rate_date, base, to_currency) WHERE purpose = 'VALUATION' DO UPDATE
              SET rate = EXCLUDED.rate,
                  source = EXCLUDED.source,
                  method = EXCLUDED.method,
                  source_rate_date = EXCLUDED.source_rate_date,
                  source_reference = EXCLUDED.source_reference
            """)) {
      statement.setObject(1, HappyInvestorTestData.REFERENCE_DATE);
      statement.setObject(2, HappyInvestorTestData.REFERENCE_DATE);
      statement.setObject(3, HappyInvestorTestData.REFERENCE_DATE);
      statement.executeUpdate();
    }
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @Test
  void readsCanonicalHappyInvestorPersistedFactsThroughRealReaders() throws Exception {
    InvestmentProfile profile = profiles.loadProfile(HappyInvestorTestData.PORTFOLIO_ID);
    Map<AssetHorizon, Map<EconomicBucket, BigDecimal>> allocations =
        profile.allocations().stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    a -> a.assetHorizon(),
                    java.util.stream.Collectors.toMap(
                        a -> a.bucket(), a -> a.value(), BigDecimal::add)));

    assertThat(profile.portfolioId()).isEqualTo(HappyInvestorTestData.PORTFOLIO_ID);
    assertThat(profile.currency()).isEqualTo(HappyInvestorTestData.REPORTING_CURRENCY);
    assertThat(profile.marketPortfolioValue())
        .isEqualByComparingTo(HappyInvestorProfileFacts.MARKET_PORTFOLIO_VALUE);
    assertThat(profile.longTermAssetValue())
        .isEqualByComparingTo(HappyInvestorProfileFacts.LONG_TERM_ASSET_VALUE);
    assertThat(profile.totalNetWorth())
        .isEqualByComparingTo(HappyInvestorProfileFacts.TOTAL_NET_WORTH);
    assertThat(profile.liquidAssets())
        .isEqualByComparingTo(HappyInvestorProfileFacts.LIQUID_ASSETS);
    assertThat(profile.illiquidAssets())
        .isEqualByComparingTo(HappyInvestorProfileFacts.ILLIQUID_ASSETS);
    assertThat(allocations.get(AssetHorizon.SHORT_TERM).get(EconomicBucket.EQUITY))
        .isEqualByComparingTo(HappyInvestorProfileFacts.EQUITY_ALLOCATION);
    assertThat(allocations.get(AssetHorizon.LONG_TERM).get(EconomicBucket.REAL_ESTATE))
        .isEqualByComparingTo(HappyInvestorProfileFacts.REAL_ESTATE_ALLOCATION);
    assertThat(allocations.get(AssetHorizon.LONG_TERM).get(EconomicBucket.LIQUID_CASH))
        .isEqualByComparingTo(HappyInvestorProfileFacts.CASH_ALLOCATION);
    assertThat(allocations.get(AssetHorizon.LONG_TERM).get(EconomicBucket.OTHER))
        .isEqualByComparingTo(HappyInvestorProfileFacts.OTHER_ALLOCATION);
    assertThat(allocations.get(AssetHorizon.LONG_TERM).get(EconomicBucket.FIXED_INCOME))
        .isEqualByComparingTo(HappyInvestorProfileFacts.FIXED_INCOME_ALLOCATION);
    assertThat(profile.currentRentalIncome())
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL);
    assertThat(profile.currentBondIncome())
        .isEqualByComparingTo(
            HappyInvestorLongTermFacts.TREASURY_PRINCIPAL
                .multiply(HappyInvestorLongTermFacts.TREASURY_ANNUAL_RATE)
                .multiply(new BigDecimal("0.81")));
    assertThat(profile.retirementReserve())
        .isEqualByComparingTo(HappyInvestorProfileFacts.RETIREMENT_RESERVE);
    assertThat(profile.investmentCapital())
        .isEqualByComparingTo(HappyInvestorProfileFacts.INVESTMENT_CAPITAL);
    assertThat(profile.incomeSummary().marketIncomeYtd())
        .isEqualByComparingTo(HappyInvestorProfileFacts.MARKET_INCOME_YTD);
    assertThat(profile.incomeSummary().marketAnnualIncome())
        .isEqualByComparingTo(HappyInvestorProfileFacts.MARKET_ANNUAL_INCOME);
    assertThat(profile.incomeSummary().marketNetYield())
        .isEqualByComparingTo(HappyInvestorProfileFacts.MARKET_NET_YIELD);
    assertThat(profile.incomeSummary().longTermAnnualIncome())
        .isEqualByComparingTo(HappyInvestorProfileFacts.LONG_TERM_ANNUAL_INCOME);
    assertThat(profile.incomeSummary().longTermNetYield())
        .isEqualByComparingTo(HappyInvestorProfileFacts.LONG_TERM_NET_YIELD);
    assertThat(profile.incomeSummary().combinedAnnualIncome())
        .isEqualByComparingTo(HappyInvestorProfileFacts.COMBINED_ANNUAL_INCOME);
    assertThat(profile.incomeSummary().combinedNetYield())
        .isEqualByComparingTo(HappyInvestorProfileFacts.COMBINED_NET_YIELD);
    BigDecimal allocationTotal =
        profile.allocations().stream()
            .map(allocation -> allocation.value())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal classifiedTotal =
        profile
            .allocationReconciliation()
            .shortTerm()
            .classifiedValue()
            .add(profile.allocationReconciliation().longTerm().classifiedValue());
    assertThat(allocationTotal).isEqualByComparingTo(classifiedTotal);
    assertThat(profile.allocationReconciliation().balanced()).isTrue();
    assertThat(profile.totalNetWorth())
        .isEqualByComparingTo(profile.marketPortfolioValue().add(profile.longTermAssetValue()));
    assertThat(profile.liquidAssets().add(profile.illiquidAssets()))
        .isEqualByComparingTo(profile.totalNetWorth());
    assertThat(profile.illiquidAssets())
        .isGreaterThan(
            profile.allocations().stream()
                .filter(allocation -> allocation.bucket() == EconomicBucket.REAL_ESTATE)
                .map(allocation -> allocation.value())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    assertThat(profile.longTermPlanningState()).isNotNull();
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> profiles.loadProfile(999999L))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void historicalMarketValueInitializationIsNotReturn() {
    var boundary =
        jdbc.queryForMap(
            """
            SELECT initialization_adjustment, total_profit, daily_return_pct
            FROM investory.app_v_portfolio_performance_daily
            WHERE portfolio_id = ? AND snapshot_date = DATE '2025-01-01'
            """,
            HappyInvestorTestData.PORTFOLIO_ID);

    assertThat((BigDecimal) boundary.get("initialization_adjustment"))
        .isEqualByComparingTo("142857.93602600");
    assertThat((BigDecimal) boundary.get("total_profit")).isEqualByComparingTo("0");
    assertThat((BigDecimal) boundary.get("daily_return_pct")).isEqualByComparingTo("0");

    var kpi = investmentDashboard.loadPerformanceKpi(HappyInvestorTestData.PORTFOLIO_ID);
    assertThat(kpi.historicalAnnualizedReturn()).isNotNull();
    assertThat(kpi.historicalAnnualizedReturn().abs()).isLessThan(BigDecimal.ONE);
    assertThat(kpi.expectedAnnualReturn().abs()).isLessThan(BigDecimal.ONE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("portfolioShapes")
  void readsEmptyAndPartialPortfoliosWithoutCrossPortfolioLeakage(String shape) {
    Long portfolioId =
        switch (shape) {
          case "empty" -> 98001L;
          case "long-term-only" -> {
            prepareLongTermOnlyPortfolio(98002L);
            yield 98002L;
          }
          case "brokerage-only" -> {
            archiveCanonicalLongTermAssets();
            yield HappyInvestorTestData.PORTFOLIO_ID;
          }
          case "mixed" -> HappyInvestorTestData.PORTFOLIO_ID;
          default -> throw new IllegalArgumentException("Unknown profile shape: " + shape);
        };
    if (shape.equals("empty")) insertPortfolio(portfolioId, "Empty Profile Portfolio");
    if (!shape.equals("mixed") && !shape.equals("brokerage-only")) {
      projectionRefresh.refreshApplicationViews(
          PortfolioProjectionRefreshService.ApplicationRefreshScope.FULL);
    }

    InvestmentProfile profile = profiles.loadProfile(portfolioId);

    assertThat(profile.portfolioId()).isEqualTo(portfolioId);
    assertThat(profile.totalNetWorth())
        .isEqualByComparingTo(profile.marketPortfolioValue().add(profile.longTermAssetValue()));
    assertThat(profile.liquidAssets().add(profile.illiquidAssets()))
        .isEqualByComparingTo(profile.totalNetWorth());
    assertThat(profile.allocationReconciliation().balanced()).isTrue();
    assertThat(
            profile.allocations().stream()
                .map(a -> a.value())
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(
            profile
                .allocationReconciliation()
                .shortTerm()
                .classifiedValue()
                .add(profile.allocationReconciliation().longTerm().classifiedValue()));
    assertThat(profile.retirementReserve()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    assertThat(profile.investmentCapital()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    if (shape.equals("empty")) {
      assertThat(profile.totalNetWorth()).isZero();
      assertThat(profile.retirementReserve()).isZero();
      assertThat(profile.investmentCapital()).isZero();
    } else if (shape.equals("long-term-only")) {
      assertThat(profile.marketPortfolioValue()).isZero();
      assertThat(profile.longTermAssetValue()).isEqualByComparingTo("1500");
      assertThat(profile.retirementReserve()).isEqualByComparingTo("500");
      assertThat(profile.investmentCapital()).isZero();
    } else if (shape.equals("brokerage-only")) {
      assertThat(profile.longTermAssetValue()).isZero();
      assertThat(profile.totalNetWorth()).isEqualByComparingTo(profile.marketPortfolioValue());
    } else {
      assertThat(profile.longTermAssetValue())
          .isEqualByComparingTo(HappyInvestorProfileFacts.LONG_TERM_ASSET_VALUE);
    }
  }

  @Test
  void repeatedProfileReadsDoNotMutateSourcePersistence() {
    Map<String, String> before = sourceState();

    profiles.loadProfile(HappyInvestorTestData.PORTFOLIO_ID);
    profiles.loadProfile(HappyInvestorTestData.PORTFOLIO_ID);

    assertThat(sourceState()).isEqualTo(before);
  }

  private static java.util.stream.Stream<String> portfolioShapes() {
    return java.util.stream.Stream.of("empty", "brokerage-only", "long-term-only", "mixed");
  }

  private void insertPortfolio(Long id, String name) {
    jdbc.update(
        "insert into portfolios(id, name, base_currency, local_currency, user_id) values (?, ?, 'USD', 'USD', 1)",
        id,
        name);
  }

  private void prepareLongTermOnlyPortfolio(Long id) {
    insertPortfolio(id, "Long-Term Only Profile Portfolio");
    jdbc.update(
        "insert into real_estate(id, portfolio_id, name, currency, value, tax_base, acquisition_date, notes) values (98021, ?, 'Profile property', 'USD', 1000, 0, DATE '2024-01-01', 'Profile shape')",
        id);
    jdbc.update(
        "insert into cash_reserve(id, portfolio_id, name, currency, value, acquisition_date, interest_rate, maturity_date, notes) values (98022, ?, 'Profile reserve', 'USD', 500, DATE '2024-01-01', 0, NULL, 'Profile shape')",
        id);
  }

  private void archiveCanonicalLongTermAssets() {
    jdbc.update(
        "update real_estate set archived_at = DATE '2025-12-31' where portfolio_id = ?",
        HappyInvestorTestData.PORTFOLIO_ID);
    jdbc.update(
        "update bond set archived_at = DATE '2025-12-31' where portfolio_id = ?",
        HappyInvestorTestData.PORTFOLIO_ID);
    jdbc.update(
        "update cash_reserve set archived_at = DATE '2025-12-31' where portfolio_id = ?",
        HappyInvestorTestData.PORTFOLIO_ID);
    jdbc.update(
        "update personal_asset set archived_at = DATE '2025-12-31' where portfolio_id = ?",
        HappyInvestorTestData.PORTFOLIO_ID);
  }

  private Map<String, String> sourceState() {
    Map<String, String> state = new HashMap<>();
    for (String table :
        List.of(
            "portfolios",
            "accounts",
            "assets",
            "cash_operations",
            "positions",
            "real_estate",
            "bond",
            "cash_reserve",
            "personal_asset",
            "rental_contract",
            "rental_contract_term",
            "long_term_asset_history",
            "long_term_asset_archive_interval")) {
      state.put(
          table,
          jdbc.queryForObject(
              "select md5(coalesce(string_agg(to_jsonb(row_data)::text, '|' order by to_jsonb(row_data)::text), '')) from (select * from investory."
                  + table
                  + ") row_data",
              String.class));
    }
    return state;
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock profileIntegrationClock() {
      return Clock.fixed(Instant.parse("2025-12-31T12:00:00Z"), ZoneId.of("Europe/Warsaw"));
    }
  }
}
