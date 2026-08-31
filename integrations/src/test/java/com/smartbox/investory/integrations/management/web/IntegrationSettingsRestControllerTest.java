package com.smartbox.investory.integrations.management.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationJobCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.*;
import com.smartbox.investory.integrations.management.model.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Integration Settings Rest Controller")
class IntegrationSettingsRestControllerTest {
  private IntegrationSettingsFacade facade;
  private MockMvc mvc;
  private final ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setUp() {
    facade = mock(IntegrationSettingsFacade.class);
    mvc = MockMvcBuilders.standaloneSetup(new IntegrationSettingsRestController(facade)).build();
  }

  @DisplayName("crud Endpoints Pass Every Configuration Field And Secret Field")
  @Test
  void crudEndpointsPassEveryConfigurationFieldAndSecretField() throws Exception {
    var payload =
        new IntegrationSettingsRestController.Payload(
            Map.of("baseUrl", "https://example.test", "model", "gpt-test"),
            Map.of("apiKey", "secret-value"),
            Set.of("oldSecret"));
    when(facade.saveConfiguration(any())).thenReturn(null);
    when(facade.testConnection(any())).thenReturn(new ConnectionTestResult(true, true, "ok"));
    when(facade.setEnabled(IntegrationType.AI, "openai", true)).thenReturn(null);
    when(facade.saveJob(any())).thenReturn(null);

    mvc.perform(
            put("/api/v1/admin/integrations/AI/openai")
                .contentType("application/json")
                .content(json.writeValueAsBytes(payload)))
        .andExpect(status().isOk());
    var save = org.mockito.ArgumentCaptor.forClass(IntegrationSettingsCommand.class);
    verify(facade).saveConfiguration(save.capture());
    org.assertj.core.api.Assertions.assertThat(save.getValue().type())
        .isEqualTo(IntegrationType.AI);
    org.assertj.core.api.Assertions.assertThat(save.getValue().pluginId()).isEqualTo("openai");
    org.assertj.core.api.Assertions.assertThat(save.getValue().configuration())
        .containsEntry("baseUrl", "https://example.test")
        .containsEntry("model", "gpt-test");
    org.assertj.core.api.Assertions.assertThat(save.getValue().secrets())
        .containsEntry("apiKey", "secret-value");
    org.assertj.core.api.Assertions.assertThat(save.getValue().clearSecrets())
        .contains("oldSecret");

    mvc.perform(
            post("/api/v1/admin/integrations/AI/openai/test")
                .contentType("application/json")
                .content(json.writeValueAsBytes(payload)))
        .andExpect(status().isOk());
    verify(facade).testConnection(any(IntegrationSettingsCommand.class));
    mvc.perform(
            put("/api/v1/admin/integrations/AI/openai/enabled")
                .contentType("application/json")
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk());
    verify(facade).setEnabled(IntegrationType.AI, "openai", true);

    mvc.perform(
            put("/api/v1/admin/integrations/AI/openai/jobs/analysis")
                .contentType("application/json")
                .content(
                    "{\"enabled\":true,\"cron\":\"0 0 * * * *\",\"timezone\":\"Europe/Warsaw\"}"))
        .andExpect(status().isOk());
    verify(facade).saveJob(any(IntegrationJobCommand.class));
  }

  @DisplayName("list Get And Job Endpoints Are Mapped")
  @Test
  void listGetAndJobEndpointsAreMapped() throws Exception {
    when(facade.listIntegrations()).thenReturn(java.util.List.of());
    when(facade.getIntegration(IntegrationType.MARKET_DATA, "twelvedata")).thenReturn(null);
    when(facade.saveJob(any())).thenReturn(null);
    mvc.perform(get("/api/v1/admin/integrations")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/integrations/MARKET_DATA/twelvedata"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/admin/integrations/MARKET_DATA/twelvedata/jobs/refresh-prices")
                .contentType("application/json")
                .content(
                    "{\"enabled\":true,\"cron\":\"0 0 * * * *\",\"timezone\":\"Europe/Warsaw\"}"))
        .andExpect(status().isOk());
    verify(facade).saveJob(any(IntegrationJobCommand.class));
  }
}
