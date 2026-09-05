package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Investment Profile Template Contract")
class InvestmentProfileTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/investment-profile.html");
  private static final Path SHARED_NAV =
      Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html");

  @DisplayName("profile Uses Compact Header And Structure Dimensions")
  @Test
  void profileUsesCompactHeaderAndStructureDimensions() throws Exception {
    String profileHtml = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    String html = profileHtml + Files.readString(SHARED_NAV, StandardCharsets.UTF_8);
    assertThat(html)
        .contains("iv-planning-topbar")
        .contains("planningHeader('profile', ${profile.portfolioId}")
        .contains("profileHeaderNetWorth")
        .contains("profileHeaderIncome")
        .contains("profileAnnualCost")
        .contains("profileAnnualCostMeta")
        .contains("profileMarketAnnualizedReturn")
        .contains("Annual income (net)")
        .contains("Annual cost / year")
        .contains("Market investments")
        .contains("Long-term assets")
        .contains("Income sources")
        .contains("Received YTD")
        .contains("profileLongTermYtdIncome");
    assertThat(profileHtml)
        .doesNotContain("Investment result")
        .doesNotContain("profileMarketInvestmentResult");
    assertThat(profileHtml)
        .contains(
            "th:href=\"@{/portfolios/{portfolioId}/dashboard(portfolioId=${profile.portfolioId})}\"");
    assertThat(profileHtml)
        .doesNotContain("Investment profile")
        .doesNotContain("Combined view of traded investments")
        .doesNotContain("Expected annual income")
        .doesNotContain("'Market return'")
        .doesNotContain("<span>Basis</span>")
        .doesNotContain("<th>Liquidity</th>")
        .doesNotContain("Liquid assets ${");
  }

  @DisplayName("profile Keeps Allocation And Income Semantics And Global Theme")
  @Test
  void profileKeepsAllocationAndIncomeSemanticsAndGlobalTheme() throws Exception {
    String html =
        Files.readString(TEMPLATE, StandardCharsets.UTF_8)
            + Files.readString(SHARED_NAV, StandardCharsets.UTF_8);
    assertThat(html)
        .contains("iv-profile-allocation__swatch")
        .contains("allocation.bucket")
        .contains("allocation.compactValueDisplay")
        .contains("profile.marketPortfolioValueCompactDisplay")
        .contains("profile.longTermAssetValueCompactDisplay")
        .contains("profileMarketYtdIncome")
        .contains("profile.marketAnnualIncomeDisplay")
        .contains("profile.incomeSummary.longTermAnnualIncomeCompactDisplay")
        .contains("Annualized return")
        .contains("profileMarketKpiMeta")
        .contains("Annualized return (net)")
        .contains("iv-profile-sources__grid")
        .contains("iv-profile-allocation")
        .contains("th:if=\"${allocation.nonZero}\"")
        .contains(
            "Short-term assets ${profile.shortTermAssetsPercentageDisplay} · Long-term assets")
        .contains("allocation.horizonLabel")
        .contains("<th>Asset type</th>")
        .doesNotContain("iv-allocation-legend")
        .doesNotContain("iv-profile-income-cards")
        .doesNotContain("Portfolio structure")
        .doesNotContain("Expected long-term net income · annual")
        .doesNotContain("<small>annualized</small>")
        .doesNotContain("iv-profile-allocation__summary")
        .doesNotContain("Combined informational view")
        .contains("fragments/theme-head :: theme");
  }

  @DisplayName("zero Total Percentage Is Handled By Profile Read Model")
  @Test
  void zeroTotalPercentageIsHandledByProfileReadModel() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            com.smartbox.investory.shared.currency.CurrencyType.USD,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.util.List.of(),
            null,
            null,
            new com.smartbox.investory.profile.api.model.ProfileAssetProjection(
                java.util.List.of(),
                java.math.BigDecimal.ZERO,
                0,
                com.smartbox.investory.shared.projection.ProjectionSource.PROJECTED),
            (java.math.BigDecimal.ZERO == null
                ? java.math.BigDecimal.ZERO
                : java.math.BigDecimal.ZERO),
            java.math.BigDecimal.ZERO
                .subtract(
                    (java.math.BigDecimal.ZERO == null
                        ? java.math.BigDecimal.ZERO
                        : java.math.BigDecimal.ZERO))
                .max(java.math.BigDecimal.ZERO),
            com.smartbox.investory.testsupport.profile.ProfileIncomeSummaryFixtures.annualIncome(
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO),
            com.smartbox.investory.profile.api.model.ProfileAllocationReconciliation.EMPTY);
    assertThat(profile.liquidAssetsPercentage()).isZero();
    assertThat(profile.illiquidAssetsPercentage()).isZero();
  }
}
