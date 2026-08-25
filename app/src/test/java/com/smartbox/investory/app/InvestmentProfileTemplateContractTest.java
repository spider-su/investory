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
        .contains("profile.expectedLongTermAssetIncome")
        .contains("Expected long-term net income")
        .contains("Market investments")
        .contains("Long-term assets")
        .contains("Portfolio structure")
        .contains("Liquidity")
        .contains("Liquid and illiquid assets");
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
        .contains("iv-allocation-legend")
        .contains("allocation.bucket")
        .contains("allocation.wholeValueDisplay")
        .contains("Market income · current period")
        .contains("Expected long-term net yield")
        .contains("iv-profile-income-cards")
        .contains("iv-profile-allocation")
        .contains("th:if=\"${allocation.nonZero}\"")
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
