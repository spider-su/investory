package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

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

  @Test
  void sharedThemeFragmentSynchronizesBothThemeAttributesAndExistingStorageKey() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/theme-head.html"),
            StandardCharsets.UTF_8);
    assertThat(html)
        .contains("investory.theme")
        .contains("dataset.theme")
        .contains("dataset.bsTheme")
        .contains("/js/theme.js");
  }
}
