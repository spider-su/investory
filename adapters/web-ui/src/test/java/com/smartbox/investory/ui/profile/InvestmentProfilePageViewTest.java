package com.smartbox.investory.ui.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.api.reporting.InvestmentDashboardApi;
import com.smartbox.investory.profile.api.model.AssetHorizon;
import com.smartbox.investory.profile.api.model.EconomicBucket;
import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.profile.api.model.Liquidity;
import com.smartbox.investory.profile.api.model.ProfileAllocation;
import com.smartbox.investory.shared.currency.CurrencyType;
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

    InvestmentProfilePageView page = InvestmentProfilePageView.from(profile);

    assertThat(page.marketPortfolioValueCompactDisplay()).isEqualTo("10.4K");
    assertThat(page.longTermAssetValueCompactDisplay()).isEqualTo("1.2M");
    assertThat(page.totalNetWorthCompactDisplay()).isEqualTo("1.21M");
    assertThat(page.allocations().getFirst().compactValueDisplay()).isEqualTo("1.2M");
    assertThat(page.allocations().getFirst().horizonLabel()).isEqualTo("Long-term asset");
  }

  @DisplayName("formats Profile Return Without Sign Or Annual Suffix")
  @Test
  void formatsProfileReturnWithoutSignOrAnnualSuffix() {
    InvestmentProfile profile = emptyProfile();

    assertThat(pageWithReturn(profile, "0.281").marketAnnualizedReturnDisplay()).isEqualTo("28.1%");
    assertThat(pageWithReturn(profile, "-0.042").marketAnnualizedReturnDisplay())
        .isEqualTo("-4.2%");
    assertThat(pageWithReturn(profile, "0").marketAnnualizedReturnDisplay()).isEqualTo("0.0%");
  }

  @DisplayName("preserves Unavailable Actuals And Retirement Cost")
  @Test
  void preservesUnavailableActualsAndRetirementCost() {
    InvestmentProfile profile = emptyProfile();
    InvestmentProfilePageView page =
        InvestmentProfilePageView.from(
            profile,
            new InvestmentDashboardApi.PerformanceKpiView(false, null, "Unavailable", null),
            InvestmentDashboardApi.InvestmentResultView.unavailable(CurrencyType.USD),
            com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
                CurrencyType.USD, 2026),
            8);

    assertThat(page.marketReceivedYtdDisplay()).isEqualTo("—");
    assertThat(page.longTermReceivedYtdDisplay()).isEqualTo("0");
    assertThat(page.annualCostDisplay()).isEqualTo("—");
    assertThat(page.annualCostMeta()).isEqualTo("No retirement plan");
  }

  @DisplayName("uses Investment Result As Market Received Ytd")
  @Test
  void usesInvestmentResultAsMarketReceivedYtd() {
    InvestmentProfilePageView page =
        InvestmentProfilePageView.from(
            emptyProfile(),
            new InvestmentDashboardApi.PerformanceKpiView(false, null, "Unavailable", null),
            new InvestmentDashboardApi.InvestmentResultView(
                true, new BigDecimal("20483"), CurrencyType.USD),
            com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
                CurrencyType.USD, 2026),
            8);

    assertThat(page.marketReceivedYtdDisplay()).isEqualTo("20,483");
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
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);

    InvestmentProfilePageView page = InvestmentProfilePageView.from(profile);

    assertThat(page.marketPortfolioMeta()).isEqualTo("11.8% of net worth");
    assertThat(page.shortTermAssetsPercentageDisplay()).isEqualTo("11.8%");
    assertThat(page.longTermAssetMeta()).isEqualTo("88.2% of net worth");
    assertThat(page.longTermAssetsPercentageDisplay()).isEqualTo("88.2%");
    assertThat(
            page.allocations().stream()
                .map(InvestmentProfilePageView.AllocationView::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(BigDecimal.ONE);
  }

  private static InvestmentProfilePageView pageWithReturn(InvestmentProfile profile, String value) {
    return InvestmentProfilePageView.from(
        profile,
        new InvestmentDashboardApi.PerformanceKpiView(
            true, new BigDecimal(value), "ignored", "2025-01-01"),
        InvestmentDashboardApi.InvestmentResultView.unavailable(CurrencyType.USD),
        com.smartbox.investory.retirement.api.model.AnnualCostView.unavailable(
            CurrencyType.USD, 2026),
        8);
  }

  private static InvestmentProfile emptyProfile() {
    return new InvestmentProfile(
        1L,
        CurrencyType.USD,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
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
