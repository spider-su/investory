package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReconciliationTemplateContractTest {
  private static final Path TEMPLATE =
      Path.of("../adapters/web-ui/src/main/resources/templates/reconciliation.html");

  @Test
  void reconciliationUsesTheCommonShellAndCompactStateBanner() throws Exception {
    String html = Files.readString(TEMPLATE, StandardCharsets.UTF_8);

    assertThat(html)
        .contains("iv-app iv-planning-page iv-report-page")
        .contains("planningHeader('reconciliation'")
        .contains("iv-reconciliation-state-banner")
        .contains("First failing checkpoint")
        .contains("#numbers.formatInteger")
        .contains("' → '")
        .doesNotContain("appHeader('Reconciliation'")
        .doesNotContain("iv-report-metrics");
  }
}
