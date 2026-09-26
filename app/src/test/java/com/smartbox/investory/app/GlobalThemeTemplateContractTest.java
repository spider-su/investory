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
          "real-estate-form.html",
          "real-estate-detail.html",
          "bond-form.html",
          "personal-asset-form.html",
          "cash-reserve-form.html",
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
        .contains("/js/investory-navigation.js")
        .doesNotContain("turbo")
        .doesNotContain("data-turbo");
  }

  @DisplayName("Dashboard import remains a normal multipart POST")
  @Test
  void dashboardImportRemainsNormalMultipartPost() throws Exception {
    String html =
        HtmlTestSupport.readTemplateWithFragments(
            Path.of("../adapters/web-ui/src/main/resources/templates/dashboard.html"));
    assertThat(html).contains("id=\"xtb-upload-form\"").contains("method=\"POST\"");
    assertThat(html).doesNotContain("data-turbo");
  }

  @DisplayName("shared navigation owns the browser lifecycle")
  @Test
  void sharedNavigationOwnsTheBrowserLifecycle() throws Exception {
    String navigation =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/investory-navigation.js"),
            StandardCharsets.UTF_8);
    String theme =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/theme.js"),
            StandardCharsets.UTF_8);
    String authorization =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/static/js/investory-authorization.js"),
            StandardCharsets.UTF_8);

    assertThat(navigation)
        .contains("document.addEventListener('DOMContentLoaded', initializePage")
        .contains("initTheme()")
        .contains("applyAuthorizationCapabilities()")
        .doesNotContain("turbo:");
    assertThat(theme).doesNotContain("DOMContentLoaded", "turbo:");
    assertThat(authorization).doesNotContain("DOMContentLoaded", "turbo:");
  }
}
