package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartbox.investory.investment.projection.PortfolioProjectionRefreshService;
import com.smartbox.investory.investment.projection.PortfolioProjectionService;
import com.smartbox.investory.longterm.api.LongTermAssetProfileReader;
import com.smartbox.investory.profile.api.ProfileSnapshotReader;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.model.AnalysisAvailability;
import com.smartbox.investory.retirement.api.model.PlanDetails;
import com.smartbox.investory.retirement.api.model.PlanningTimeline;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.RetirementProjection;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.retirement.planning.application.RetirementAnalysisService;
import com.smartbox.investory.retirement.planning.projection.RetirementProjectionService;
import com.smartbox.investory.retirement.planning.timeline.PlanningTimelineFacade;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.*;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** End-to-end retirement contract over the canonical HappyInvestor database fixture. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-fast")
@EnableCaching
@Import(RetirementGoldenScenarioIntegrationTest.FixedClockConfig.class)
class RetirementGoldenScenarioIntegrationTest {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("retirement_golden_scenario");

  @Autowired private DataSource dataSource;
  @Autowired private PortfolioProjectionService projections;
  @Autowired private PortfolioProjectionRefreshService projectionRefresh;
  @Autowired private ProfileSnapshotReader profiles;
  @Autowired private LongTermAssetProfileReader longTermAssets;
  @Autowired private RetirementPlanApi plans;
  @Autowired private RetirementProjectionService projectionService;
  @Autowired private PlanningTimelineFacade timelines;
  @Autowired private RetirementAnalysisService analyses;

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
        java.util.Set.of(
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

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void canonicalHappyInvestorFlowsThroughProfilePlanBridgeSimulationTimelineAndAnalysis() {
    InvestmentProfile profile = profiles.loadProfile(HappyInvestorTestData.PORTFOLIO_ID);
    assertThat(profile.totalNetWorth())
        .isEqualByComparingTo(HappyInvestorProfileFacts.TOTAL_NET_WORTH);
    assertThat(profile.currentRentalIncome())
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL);
    assertThat(profile.currentBondIncome())
        .isEqualByComparingTo(
            HappyInvestorLongTermFacts.TREASURY_PRINCIPAL
                .multiply(HappyInvestorLongTermFacts.TREASURY_ANNUAL_RATE)
                .multiply(new java.math.BigDecimal("0.81")));

    PlanDetails plan =
        plans.details(HappyInvestorTestData.PORTFOLIO_ID, HappyInvestorPlanFacts.SEED_PLAN_ID);
    assertThat(plan.name()).isEqualTo(HappyInvestorPlanFacts.NAME);
    assertThat(plan.id()).isEqualTo(HappyInvestorPlanFacts.SEED_PLAN_ID);
    assertThat(plan.assumptions().retirementAge()).isEqualTo(HappyInvestorPlanFacts.RETIREMENT_AGE);

    var longTerm =
        longTermAssets.snapshot(
            HappyInvestorTestData.PORTFOLIO_ID, HappyInvestorTestData.REFERENCE_DATE);
    assertThat(longTerm.summary().totalCurrentValue())
        .isEqualByComparingTo(HappyInvestorLongTermFacts.LONG_TERM_TOTAL);
    assertThat(longTerm.annualSnapshot().rentalIncome())
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL);

    RetirementProjection projection =
        projectionService.load(
            HappyInvestorTestData.PORTFOLIO_ID, HappyInvestorPlanFacts.SEED_PLAN_ID);
    assertThat(projection.forward().currentYearBridge()).isNotNull();
    assertThat(projection.scenarioResults()).containsKey(SimulationScenario.BASE);
    assertThat(projection.scenarioResults().get(SimulationScenario.BASE).years()).isNotEmpty();
    assertThat(projection.scenarioResults().get(SimulationScenario.BASE).simulationFailed())
        .isFalse();
    var baseYears = projection.scenarioResults().get(SimulationScenario.BASE).years();
    var bridge = projection.forward().currentYearBridge();
    assertThat(bridge.expectedEnd(EconomicBucket.LIQUID_CASH))
        .isEqualByComparingTo(HappyInvestorRetirementFacts.BRIDGE_CASH_END);
    assertThat(bridge.expectedEnd(EconomicBucket.FIXED_INCOME))
        .isEqualByComparingTo(HappyInvestorRetirementFacts.BRIDGE_BONDS_END);
    assertThat(bridge.expectedEnd(EconomicBucket.EQUITY))
        .isEqualByComparingTo(HappyInvestorRetirementFacts.BRIDGE_EQUITIES_END);
    assertThat(bridge.expectedEnd(EconomicBucket.REAL_ESTATE))
        .isEqualByComparingTo(HappyInvestorRetirementFacts.BRIDGE_REAL_ESTATE_END);

    var firstYear = baseYears.stream().filter(y -> y.year() == 2026).findFirst().orElseThrow();
    assertThat(firstYear.employmentIncome())
        .isEqualByComparingTo(HappyInvestorPlanFacts.ANNUAL_EMPLOYMENT_INCOME);
    assertThat(firstYear.preRetirementContribution())
        .isEqualByComparingTo(HappyInvestorPlanFacts.ANNUAL_PRE_RETIREMENT_CONTRIBUTION);
    assertThat(firstYear.rentalIncome())
        .isEqualByComparingTo(HappyInvestorRetirementFacts.FIRST_PROJECTED_RENTAL_INCOME);
    assertThat(firstYear.equityGain())
        .isEqualByComparingTo(HappyInvestorRetirementFacts.FIRST_PROJECTED_EQUITY_RETURN);
    assertThat(firstYear.fixedIncomeEnd())
        .isEqualByComparingTo(HappyInvestorRetirementFacts.FIRST_PROJECTED_BOND_END);
    assertThat(firstYear.equityEnd())
        .isEqualByComparingTo(HappyInvestorRetirementFacts.FIRST_PROJECTED_EQUITY_END);
    assertThat(firstYear.endNetWorth())
        .isEqualByComparingTo(HappyInvestorRetirementFacts.FIRST_PROJECTED_END_NET_WORTH);

    var retirementYear =
        baseYears.stream()
            .filter(y -> y.year() == HappyInvestorRetirementFacts.RETIREMENT_BOUNDARY_YEAR)
            .findFirst()
            .orElseThrow();
    assertThat(retirementYear.employmentIncome()).isZero();
    assertThat(retirementYear.coreExpenses())
        .isEqualByComparingTo(new java.math.BigDecimal("42000"));
    var pensionYear =
        baseYears.stream()
            .filter(y -> y.year() == HappyInvestorRetirementFacts.PENSION_BOUNDARY_YEAR)
            .findFirst()
            .orElseThrow();
    assertThat(pensionYear.pensionIncome())
        .isEqualByComparingTo(HappyInvestorPlanFacts.ANNUAL_PENSION);

    var finalYear = baseYears.getLast();
    assertThat(finalYear.endNetWorth())
        .isCloseTo(
            HappyInvestorRetirementFacts.FINAL_END_NET_WORTH,
            within(new java.math.BigDecimal("0.01")));
    assertThat(finalYear.unfundedAmount()).isZero();

    PlanningTimeline timeline =
        timelines.loadForwardTimeline(
            HappyInvestorTestData.PORTFOLIO_ID, projection, SimulationScenario.BASE);
    assertThat(timeline.years()).isNotEmpty();
    assertThat(timeline.years().getLast().year())
        .isEqualTo(HappyInvestorRetirementFacts.LAST_PROJECTED_YEAR);
    assertThat(timeline.years().getLast().age())
        .isEqualTo(HappyInvestorRetirementFacts.LAST_PROJECTED_AGE);
    assertThat(timeline.years()).anyMatch(year -> year.state() == PlanningTimelineState.LIVE);
    assertThat(timeline.years()).anyMatch(year -> year.state() == PlanningTimelineState.PROJECTED);

    var analysis = analyses.analyze(projection);
    assertThat(analysis.state()).isNotNull();
    assertThat(analysis.sustainableSpending()).isInstanceOf(AnalysisAvailability.Available.class);
    assertThat(analysis.retirementAge()).isInstanceOf(AnalysisAvailability.Available.class);
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock retirementIntegrationClock() {
      return Clock.fixed(Instant.parse("2025-12-31T12:00:00Z"), ZoneId.of("Europe/Warsaw"));
    }
  }
}
