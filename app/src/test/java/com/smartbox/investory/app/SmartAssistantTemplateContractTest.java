package com.smartbox.investory.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SmartAssistantTemplateContractTest {
  private static final Path RESOURCES = Path.of("../adapters/web-ui/src/main/resources");

  @Test
  void dashboardExposesPortfolioScopedAssistantWithoutDuplicatingFinanceLogic() throws Exception {
    String dashboard = read("templates/dashboard.html");
    String fragment = read("templates/fragments/smart-assistant.html");
    String navigation = read("static/js/investory-navigation.js");

    assertThat(dashboard)
        .contains("fragments/smart-assistant :: assistant")
        .contains("/css/components/smart-assistant.css");
    assertThat(fragment)
        .contains("data-portfolio-id")
        .contains("${portfolioId}")
        .contains("id=\"iv-assistant-trigger\"")
        .contains("id=\"iv-assistant-panel\"")
        .contains("id=\"iv-assistant-form\"")
        .contains("maxlength=\"2000\"")
        .contains("aria-live=\"polite\"");
    assertThat(navigation).contains("initSmartAssistant()");
  }

  @Test
  void browserUsesExistingAuthorizedRagApiWithCsrfAndSafeText() throws Exception {
    String script = read("static/js/smart-assistant.js");
    assertThat(script)
        .contains("/api/v1/portfolios/")
        .contains("/rag/")
        .contains("questionMode === 'docs' ? 'ask-docs' : 'ask'")
        .contains("credentials: 'same-origin'")
        .contains("X-XSRF-TOKEN")
        .contains("textContent = text")
        .contains("AbortController")
        .contains("status === 429")
        .contains("response.status === 401 || response.status === 403")
        .contains("Array.isArray(data.sources)")
        .doesNotContain(".innerHTML", "localStorage", "sessionStorage", "marked.parse");
  }

  private static String read(String relative) throws Exception {
    return Files.readString(RESOURCES.resolve(relative), StandardCharsets.UTF_8);
  }
}
