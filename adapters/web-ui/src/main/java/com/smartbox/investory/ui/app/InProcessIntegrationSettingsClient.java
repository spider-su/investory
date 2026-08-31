package com.smartbox.investory.ui.app;

import com.smartbox.investory.integrations.management.api.IntegrationSettingsApi;
import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationJobCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InProcessIntegrationSettingsClient implements IntegrationSettingsClient {
  private final IntegrationSettingsApi integrationSettingsApi;

  @Override
  public List<IntegrationSettingsView> list() {
    return integrationSettingsApi.listIntegrations();
  }

  @Override
  public IntegrationSettingsView save(
      IntegrationType type,
      String pluginId,
      Map<String, String> configuration,
      Map<String, String> secrets,
      Set<String> clearSecrets) {
    return integrationSettingsApi.saveConfiguration(
        new IntegrationSettingsCommand(type, pluginId, configuration, secrets, clearSecrets));
  }

  @Override
  public ConnectionTestResult test(
      IntegrationType type,
      String pluginId,
      Map<String, String> configuration,
      Map<String, String> secrets,
      Set<String> clearSecrets) {
    return integrationSettingsApi.testConnection(
        new IntegrationSettingsCommand(type, pluginId, configuration, secrets, clearSecrets));
  }

  @Override
  public IntegrationSettingsView setEnabled(
      IntegrationType type, String pluginId, boolean enabled) {
    return integrationSettingsApi.setEnabled(type, pluginId, enabled);
  }

  @Override
  public IntegrationSettingsView.JobView saveJob(
      IntegrationType type,
      String pluginId,
      String jobType,
      boolean enabled,
      String cron,
      String timezone) {
    return integrationSettingsApi.saveJob(
        new IntegrationJobCommand(type, pluginId, jobType, enabled, cron, timezone, Map.of()));
  }
}
