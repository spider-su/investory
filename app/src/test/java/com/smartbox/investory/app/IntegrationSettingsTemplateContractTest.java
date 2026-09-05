package com.smartbox.investory.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Integration Settings Template Contract")
class IntegrationSettingsTemplateContractTest {
  @DisplayName("integration Settings Uses Shared Application Shell And Styled Fields")
  @Test
  void integrationSettingsUsesSharedApplicationShellAndStyledFields() throws Exception {
    String html =
        Files.readString(
            Path.of("../adapters/web-ui/src/main/resources/templates/integration-settings.html"));

    assertTrue(html.contains("fragments/theme-head :: theme"));
    assertTrue(html.contains("fragments/app-header :: appNavigation('settings', ${portfolioId})"));
    assertTrue(html.contains("iv-settings-page"));
    assertTrue(html.contains("iv-settings-card"));
    assertTrue(html.contains("iv-settings-form"));
    assertTrue(html.contains("iv-status-badge"));
    assertTrue(html.contains("Save changes"));
    assertTrue(html.contains("Enable integration"));
    assertTrue(html.contains("fragments/app-footer :: buildFooter"));
    assertTrue(html.contains("role=\"status\""));
    assertTrue(html.contains("role=\"alert\""));
    assertTrue(html.contains("Test connection"));
    assertTrue(html.contains("Automatic refresh"));
    assertTrue(html.contains("clearSecret."));
    assertTrue(html.contains("Integrations are optional"));
  }
}
