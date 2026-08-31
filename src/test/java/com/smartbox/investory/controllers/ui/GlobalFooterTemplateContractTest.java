package com.smartbox.investory.controllers.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GlobalFooterTemplateContractTest {
  @Test
  void footerIsOneSharedBuildMetadataFragment() throws Exception {
    String footer =
        Files.readString(
            Path.of("src/main/resources/templates/fragments/app-footer.html"),
            StandardCharsets.UTF_8);
    assertThat(footer)
        .contains("th:fragment=\"buildFooter\"")
        .contains("buildMetadata.displayText")
        .contains("buildMetadata.builtAt");
  }

  @Test
  void mainPagesIncludeTheSharedFooter() throws Exception {
    for (String template :
        new String[] {
          "dashboard.html",
          "long-term-assets.html",
          "investment-profile.html",
          "simulation.html",
          "reconciliation.html"
        }) {
      String html =
          Files.readString(
              Path.of("src/main/resources/templates", template), StandardCharsets.UTF_8);
      assertThat(html).as(template).contains("fragments/app-footer :: buildFooter");
    }
  }
}
