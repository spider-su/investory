package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InvestmentProfileTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/investment-profile.html");
  private static final Path SHARED_NAV =
      Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html");

  @Test
  void profileUsesCompactHeaderAndStructureDimensions() throws Exception {
    String profileHtml = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    String html = profileHtml + Files.readString(SHARED_NAV, StandardCharsets.UTF_8);
    assertThat(html)
        .contains("iv-planning-topbar")
        .contains("planningHeader('profile'")
        .contains("profileHeaderNetWorth")
        .contains("profileHeaderIncome")
        .contains("profileHeaderYield")
        .contains("Annual income")
        .contains("Annual yield")
        .contains("Market investments")
        .contains("Long-term assets")
        .contains("Income sources")
        .contains("Received YTD")
        .contains("Current contracts and rates");
    assertThat(profileHtml)
        .doesNotContain("Investment profile")
        .doesNotContain("Combined view of traded investments")
        .doesNotContain("Expected annual income");
  }

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
        .contains("profile.incomeSummary.marketIncomeYtdCompactDisplay")
        .contains("profile.incomeSummary.marketAnnualIncomeCompactDisplay")
        .contains("profile.incomeSummary.longTermAnnualIncomeCompactDisplay")
        .contains("iv-profile-sources__grid")
        .contains("iv-profile-allocation")
        .contains("th:if=\"${allocation.nonZero}\"")
        .contains("Liquid ${profile.liquidAssetsPercentageDisplay} · Illiquid")
        .doesNotContain("iv-allocation-legend")
        .doesNotContain("iv-profile-income-cards")
        .doesNotContain("Portfolio structure")
        .doesNotContain("Expected long-term net income · annual")
        .doesNotContain("iv-profile-allocation__summary")
        .doesNotContain("Combined informational view")
        .contains("fragments/theme-head :: theme");
  }

  @Test
  void zeroTotalPercentageIsHandledByProfileReadModel() {
    InvestmentProfile profile =
        new InvestmentProfile(
            1L,
            null,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.math.BigDecimal.ZERO,
            java.util.List.of(),
            java.util.List.of());
    assertThat(profile.liquidAssetsPercentage()).isZero();
    assertThat(profile.illiquidAssetsPercentage()).isZero();
  }
}
