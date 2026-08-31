package com.smartbox.investory.ui.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class IntegrationSettingsControllerTest {
  private IntegrationSettingsClient settings;
  private IntegrationSettingsController controller;

  @BeforeEach
  void setUp() {
    settings = mock(IntegrationSettingsClient.class);
    controller = new IntegrationSettingsController(settings);
  }

  @Test
  void pageCalculatesOperationalCounts() {
    IntegrationSettingsView healthy = view(true, true, false, "READY");
    IntegrationSettingsView failed = view(false, false, true, "TEST_FAILED");
    when(settings.list()).thenReturn(List.of(healthy, failed));
    var model = new ConcurrentModel();

    assertThat(controller.page(model)).isEqualTo("integration-settings");
    assertThat(model.getAttribute("activeCount")).isEqualTo(1L);
    assertThat(model.getAttribute("attentionCount")).isEqualTo(1L);
    assertThat(model.getAttribute("setupCount")).isEqualTo(1L);
    assertThat(model.getAttribute("failedCount")).isEqualTo(1L);
  }

  @Test
  void saveSeparatesConfigurationSecretsAndClearRequests() {
    var redirect = new RedirectAttributesModelMap();
    controller.save(
        IntegrationType.MARKET_DATA,
        "twelve-data",
        Map.of(
            "baseUrl", "https://market.test",
            "secret.apiKey", "secret",
            "clearSecret.old", "true",
            "enabled", "true"),
        redirect);

    verify(settings)
        .save(
            IntegrationType.MARKET_DATA,
            "twelve-data",
            Map.of("baseUrl", "https://market.test"),
            Map.of("apiKey", "secret"),
            java.util.Set.of("old"));
    assertThat(redirect.getFlashAttributes().get("success")).isEqualTo("Settings saved");
  }

  @Test
  void testAndFailuresReturnStableFlashMessages() {
    when(settings.test(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            anyMap(),
            anyMap(),
            anySet()))
        .thenReturn(new ConnectionTestResult(true, false, "offline"));
    var redirect = new RedirectAttributesModelMap();
    controller.save(IntegrationType.MARKET_DATA, "twelve-data", Map.of("action", "test"), redirect);
    assertThat(redirect.getFlashAttributes().get("error").toString()).contains("offline");

    when(settings.setEnabled(IntegrationType.MARKET_DATA, "twelve-data", true))
        .thenThrow(new IllegalStateException("invalid config"));
    redirect = new RedirectAttributesModelMap();
    controller.enabled(IntegrationType.MARKET_DATA, "twelve-data", true, redirect);
    assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("invalid config");
  }

  @Test
  void jobsDelegateScheduleAndReportSuccess() {
    var redirect = new RedirectAttributesModelMap();
    controller.job(
        IntegrationType.MARKET_DATA, "twelve-data", "QUOTES", true, "0 0 * * * *", "UTC", redirect);
    verify(settings)
        .saveJob(IntegrationType.MARKET_DATA, "twelve-data", "QUOTES", true, "0 0 * * * *", "UTC");
    assertThat(redirect.getFlashAttributes().get("success")).isEqualTo("Schedule saved");
  }

  private static IntegrationSettingsView view(
      boolean enabled, boolean configured, boolean attention, String status) {
    IntegrationSettingsView view = mock(IntegrationSettingsView.class);
    when(view.enabled()).thenReturn(enabled);
    when(view.configured()).thenReturn(configured);
    when(view.needsAttention()).thenReturn(attention);
    when(view.status()).thenReturn(status);
    return view;
  }
}
