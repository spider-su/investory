package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedNavigationTemplateContractTest {

  @Test
  void investmentNavigationPreservesPortfolioId() throws Exception {
    String template =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/fragments/app-header.html"));

    assertThat(template).contains("th:href=\"@{/dashboard(portfolioId=${portfolioId})}\"");
  }
}
