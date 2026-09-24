package com.smartbox.investory.ui.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.ui.investment.InvestmentPerformanceKpi;
import com.smartbox.investory.ui.investment.InvestmentResult;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Investment Profile Page View")
class InvestmentProfilePageViewTest {

  @DisplayName("formats Compact Summary Values In The Web Adapter")
  @Test
  void formatsCompactSummaryValuesInTheWebAdapter() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("10400"),
            new BigDecimal("1200000"),
            new BigDecimal("1210400"),
            new BigDecimal("10400"),
            new BigDecimal("1200000"),
            List.of(
                new ProfileAllocation(
                    EconomicBucket.REAL_ESTATE,
                    new BigDecimal("1200000"),
                    new BigDecimal("0.75"),
                    Liquidity.ILLIQUID,
                    AssetHorizon.LONG_TERM)),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("10400") == null ? java.math.BigDecimal.ZERO : new BigDecimal("10400")),
            new BigDecimal("10400")
                .subtract(
                    (new BigDecimal("10400") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("10400")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                new BigDecimal("10400"),
                new BigDecimal("10400"),
                BigDecimal.ZERO,
                new BigDecimal("1200000"),
                BigDecimal.ZERO,
                new BigDecimal("1210400")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);

    InvestmentProfilePageView page = pageWithDefaults(profile);

    assertThat(page.marketPortfolioValueCompactDisplay()).isEqualTo("10.4K");
    assertThat(page.longTermAssetValueCompactDisplay()).isEqualTo("1.20M");
    assertThat(page.totalNetWorthCompactDisplay()).isEqualTo("1.21M");
    assertThat(page.allocations().getFirst().compactValueDisplay()).isEqualTo("1.20M");
    assertThat(page.allocations().getFirst().horizonLabel()).isEqualTo("Long-term asset · locked");
  }

  @DisplayName("shows Current Market Value Without Deriving It From Profit")
  @Test
  void showsCurrentMarketValueAlongsideYtdInvestmentProfit() {
    InvestmentProfilePageView page =
        InvestmentProfilePageView.from(
            emptyProfile(),
            new InvestmentPerformanceKpi(
                false,
                null,
                "Unavailable",
                null,
                null,
                "Unavailable",
                null,
                "Unavailable",
                null,
                null),
            new InvestmentResult(true, new BigDecimal("20483"), CurrencyType.USD),
            com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
                CurrencyType.USD, 2026),
            8);

    assertThat(page.marketPortfolioValueCompactDisplay()).isEqualTo("100.0K");
  }

  @DisplayName("formats Profile Return Without Sign Or Annual Suffix")
  @Test
  void formatsProfileReturnWithoutSignOrAnnualSuffix() {
    InvestmentProfile profile = emptyProfile();

    assertThat(pageWithReturn(profile, "0.281").expectedAnnualReturnDisplay()).isEqualTo("28.1%");
    assertThat(pageWithReturn(profile, "-0.042").expectedAnnualReturnDisplay()).isEqualTo("-4.2%");
    assertThat(pageWithReturn(profile, "0").expectedAnnualReturnDisplay()).isEqualTo("0.0%");
  }

  @DisplayName("preserves Unavailable Actuals And Retirement Cost")
  @Test
  void preservesUnavailableActualsAndRetirementCost() {
    InvestmentProfile profile = emptyProfile();
    InvestmentProfilePageView page =
        InvestmentProfilePageView.from(
            profile,
            new InvestmentPerformanceKpi(
                false,
                null,
                "Unavailable",
                null,
                null,
                "Unavailable",
                null,
                "Unavailable",
                null,
                null),
            new InvestmentResult(false, null, CurrencyType.USD),
            com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
                CurrencyType.USD, 2026),
            8);

    assertThat(page.marketInvestmentResultYtdDisplay()).isEqualTo("—");
    assertThat(page.longTermPlannedIncomeYtdDisplay()).isEqualTo("0");
    assertThat(page.annualCostDisplay()).isEqualTo("—");
    assertThat(page.annualCostMeta()).isEqualTo("No retirement plan");
  }

  @DisplayName("uses Investment Result As Market Received Ytd")
  @Test
  void usesCurrentYearInvestmentResultWithAnAccurateLabel() {
    InvestmentProfilePageView page =
        InvestmentProfilePageView.from(
            emptyProfile(),
            new InvestmentPerformanceKpi(
                false,
                null,
                "Unavailable",
                null,
                null,
                "Unavailable",
                null,
                "Unavailable",
                null,
                null),
            new InvestmentResult(true, new BigDecimal("20483"), CurrencyType.USD),
            com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
                CurrencyType.USD, 2026),
            8);

    assertThat(page.marketInvestmentResultYtdDisplay()).isEqualTo("20.5K");
  }

  @DisplayName("shows annualized current result and separate planned-income progress")
  @Test
  void showsYtdProgressAgainstAnnualReference() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.USD,
            new BigDecimal("100000"),
            BigDecimal.ZERO,
            new BigDecimal("100000"),
            new BigDecimal("100000"),
            BigDecimal.ZERO,
            List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                new BigDecimal("48000"),
                new BigDecimal("48000"),
                new BigDecimal("12000"),
                new BigDecimal("12000"),
                new BigDecimal("36000"),
                new BigDecimal("36000")),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    InvestmentProfilePageView page =
        InvestmentProfilePageView.from(
            profile,
            new InvestmentPerformanceKpi(
                true,
                null,
                "Unavailable",
                "2026-01-01",
                null,
                "Unavailable",
                new BigDecimal("0.083"),
                "8.3%",
                null,
                "Forecast annual return"),
            new InvestmentResult(true, new BigDecimal("12000"), CurrencyType.USD),
            com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
                CurrencyType.USD, 2026),
            6);

    assertThat(page.expectedAnnualInvestmentResultDisplay()).isEqualTo("8.30K");
    assertThat(page.expectedAnnualReturnDisplay()).isEqualTo("8.3%");
    assertThat(page.incomeSummary().marketAnnualIncomeCompactDisplay()).isEqualTo("6.72K");
    assertThat(page.incomeSummary().combinedAnnualIncomeCompactDisplay()).isEqualTo("18.7K");
    assertThat(page.marketYtdReturnDisplay()).isEqualTo("144.6% of forecast annual result");
    assertThat(page.longTermPlannedIncomeYtdDisplay()).isEqualTo("6.00K");
    assertThat(page.longTermYtdProgressDisplay()).isEqualTo("50.0%");
    assertThat(page.longTermYtdProgressClass()).isEqualTo("iv-ytd-progress--positive");
  }

  @DisplayName("source Cards And Allocation Use The Same Horizon Percentages")
  @Test
  void sourceCardsAndAllocationUseTheSameHorizonPercentages() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            CurrencyType.PLN,
            new BigDecimal("168000"),
            new BigDecimal("1260000"),
            new BigDecimal("1428000"),
            new BigDecimal("168000"),
            new BigDecimal("1260000"),
            List.of(
                new ProfileAllocation(
                    EconomicBucket.EQUITY,
                    new BigDecimal("168000"),
                    new BigDecimal("0.11764706"),
                    Liquidity.LIQUID,
                    AssetHorizon.SHORT_TERM),
                new ProfileAllocation(
                    EconomicBucket.REAL_ESTATE,
                    new BigDecimal("1260000"),
                    new BigDecimal("0.88235294"),
                    Liquidity.ILLIQUID,
                    AssetHorizon.LONG_TERM)),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (new BigDecimal("168000") == null
                ? java.math.BigDecimal.ZERO
                : new BigDecimal("168000")),
            new BigDecimal("168000")
                .subtract(
                    (new BigDecimal("168000") == null
                        ? java.math.BigDecimal.ZERO
                        : new BigDecimal("168000")))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                BigDecimal.ZERO,
                new BigDecimal("168000"),
                BigDecimal.ZERO,
                new BigDecimal("1260000"),
                BigDecimal.ZERO,
                new BigDecimal("1428000")),
            new com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation(
                new com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation
                    .SourceTotal(new BigDecimal("168000"), new BigDecimal("170000")),
                com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.SourceTotal
                    .EMPTY));

    InvestmentProfilePageView page = pageWithDefaults(profile);

    assertThat(page.marketPortfolioMeta()).isEqualTo("11.8% of net worth");
    assertThat(page.shortTermAssetsPercentageDisplay()).isEqualTo("11.8%");
    assertThat(page.longTermAssetMeta()).isEqualTo("88.2% of net worth");
    assertThat(page.longTermAssetsPercentageDisplay()).isEqualTo("88.2%");
    assertThat(page.allocationApproximate()).isTrue();
    assertThat(page.allocationReconciliationMessage())
        .isEqualTo("Source totals differ; shares use classified values.");
    assertThat(
            page.allocations().stream()
                .map(InvestmentProfilePageView.AllocationView::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(BigDecimal.ONE);
  }

  private static InvestmentProfilePageView pageWithReturn(InvestmentProfile profile, String value) {
    return InvestmentProfilePageView.from(
        profile,
        new InvestmentPerformanceKpi(
            true,
            null,
            "Unavailable",
            "2025-01-01",
            null,
            "Unavailable",
            new BigDecimal(value),
            "ignored",
            null,
            null),
        new InvestmentResult(false, null, CurrencyType.USD),
        com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
            CurrencyType.USD, 2026),
        8);
  }

  private static InvestmentProfilePageView pageWithDefaults(InvestmentProfile profile) {
    return InvestmentProfilePageView.from(
        profile,
        new InvestmentPerformanceKpi(
            false, null, "Unavailable", null, null, "Unavailable", null, "Unavailable", null, null),
        new InvestmentResult(false, null, profile.currency()),
        com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
            profile.currency(), 0),
        12);
  }

  private static InvestmentProfile emptyProfile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        new BigDecimal("100000"),
        BigDecimal.ZERO,
        new BigDecimal("100000"),
        new BigDecimal("100000"),
        BigDecimal.ZERO,
        List.of(),
        null,
        null,
        new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
            List.of(),
            java.math.BigDecimal.ZERO,
            0,
            com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
        (BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO),
        BigDecimal.ZERO
            .subtract((BigDecimal.ZERO == null ? java.math.BigDecimal.ZERO : BigDecimal.ZERO))
            .max(java.math.BigDecimal.ZERO),
        com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
  }
}
