package com.smartbox.investory.profile;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/** Verifies the single-transaction whole-wealth composition boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-fast")
@Import(ProfilePersistedFactsIT.FixedClockConfig.class)
class ProfilePersistedFactsIT {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("profile_persisted_facts");

  @Autowired private ProfileSnapshotReader profiles;
  @Autowired private DataSource dataSource;
  @Autowired private PortfolioProjectionService projections;
  @Autowired private PortfolioProjectionRefreshService projectionRefresh;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void loadCanonicalHappyInvestorFixture() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-common.sql"));
      ScriptUtils.executeSqlScript(
          connection, new ClassPathResource("db/snapshot/happyinvestor-broker.sql"));
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

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    @Primary
    Clock profileIntegrationClock() {
      return Clock.fixed(Instant.parse("2025-12-31T12:00:00Z"), ZoneId.of("Europe/Warsaw"));
    }
  }
}
