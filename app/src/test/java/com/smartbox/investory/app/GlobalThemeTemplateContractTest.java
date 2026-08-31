package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Global Theme Template Contract")
class GlobalThemeTemplateContractTest {
  private static final List<String> USER_TEMPLATES =
      List.of(
          "home.html",
          "dashboard.html",
          "reconciliation.html",
          "investment-profile.html",
          "simulation.html",
          "planning-year.html",
          "long-term-assets.html",
          "long-term-asset-form.html",
          "long-term-asset-detail.html",
          "real-estate-form.html",
          "real-estate-detail.html",
          "bond-form.html",
          "bond-detail.html",
          "cash-reserve-form.html",
          "cash-reserve-detail.html",
          "dashboard/asset-detail.html",
          "dashboard/asset-not-found.html");

  @DisplayName("every User Template Uses The Shared Early Theme Initializer")
  @Test
  void everyUserTemplateUsesTheSharedEarlyThemeInitializer() throws Exception {
    for (String template : USER_TEMPLATES) {
      String html =
          Files.readString(
              Path.of("../adapters/web-ui/src/main/resources/templates", template),
              StandardCharsets.UTF_8);
      assertThat(html).as(template).contains("fragments/theme-head :: theme");
    }
  }

  @DisplayName("shared Theme Fragment Synchronizes Both Theme Attributes And Existing Storage Key")
  @Test
  void sharedThemeFragmentSynchronizesBothThemeAttributesAndExistingStorageKey() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/theme-head.html"),
            StandardCharsets.UTF_8);
    assertThat(html)
        .contains("/favicon.svg")
        .contains("investory.theme")
        .contains("dataset.theme")
        .contains("dataset.bsTheme")
        .contains("/js/theme.js")
        .contains("/js/turbo-8.0.23.js")
        .contains("/js/investory-navigation.js")
        .contains("data-turbo-track=\"reload\"");
  }

  @DisplayName("Dashboard import remains outside Turbo Drive")
  @Test
  void dashboardImportRemainsOutsideTurboDrive() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/dashboard.html"),
            StandardCharsets.UTF_8);
    assertThat(html).contains("id=\"xtb-upload-form\"").contains("data-turbo=\"false\"");
  }
}
