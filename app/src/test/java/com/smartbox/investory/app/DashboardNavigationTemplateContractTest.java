package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardNavigationTemplateContractTest {

  @Test
  void topNavigationKeepsOnlyCrossPageLinksWithoutBottomDuplicates() throws Exception {
    String template =
        Files.readString(
            Path.of("src/main/resources/templates/dashboard.html"), StandardCharsets.UTF_8);

    String fragment =
        Files.readString(
            Path.of("src/main/resources/templates/fragments/app-header.html"),
            StandardCharsets.UTF_8);

    assertThat(template)
        .contains("fragments/app-header :: appNavigation('investment', ${portfolioId})")
        .contains("dashboard.navigation.periodUrl(periodOption)")
        .doesNotContain("benchmarkAccountsSubmitted=true");
    assertThat(fragment)
        .contains("/investment-profile")
        .contains("/simulation")
        .contains("/long-term-assets")
        .contains("/dashboard/reconciliation");
    String navigation = fragment.substring(fragment.indexOf("<nav th:fragment=\"appNavigation"));
    assertThat(navigation.indexOf("Investment"))
        .isLessThan(navigation.indexOf("Long-term assets"))
        .isLessThan(navigation.indexOf("Profile"))
        .isLessThan(navigation.indexOf("Simulation"))
        .isLessThan(navigation.indexOf("Reconciliation"));
    assertThat(fragment)
        .contains("th:unless=\"${activePage == 'investment'}\"")
        .contains("class=\"is-active\" aria-current=\"page\"")
        .doesNotContain("portfolioId=1");
    assertThat(fragment).contains("Investment").doesNotContain("Back to investment");
    assertThat(Files.readString(Path.of("src/main/resources/templates/fragments/app-header.html")))
        .doesNotContain("Back to investment")
        .contains("${portfolioId}")
        .doesNotContain("workspace', current, portfolioId")
        .doesNotContain("'profile', portfolioId, netWorth");
    assertThat(fragment)
        .contains("th:href=\"@{/investment-profile(portfolioId=${portfolioId})}\"")
        .contains("th:href=\"@{/simulation(portfolioId=${portfolioId})}\"")
        .contains("th:href=\"@{/long-term-assets(portfolioId=${portfolioId})}\"");
    assertThat(fragment)
        .doesNotContain("Overview")
        .doesNotContain("Performance")
        .doesNotContain("Positions")
        .doesNotContain("Cash flows")
        .doesNotContain("Data quality");
    assertThat(template).doesNotContain("iv-risk-summary__links");
  }
}
