package com.smartbox.investory.ui.app;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.web.IntegrationSettingsRestController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class InProcessIntegrationSettingsClient implements IntegrationSettingsClient {
  private final IntegrationSettingsRestController rest;

  public InProcessIntegrationSettingsClient(IntegrationSettingsRestController rest) {
    this.rest = rest;
  }

  @Override
  public List<IntegrationSettingsView> list() {
    return rest.list();
  }

  @Override
  public IntegrationSettingsView save(
      IntegrationType type,
      String pluginId,
      Map<String, String> configuration,
      Map<String, String> secrets,
      Set<String> clearSecrets) {
    return rest.save(
        type,
        pluginId,
        new IntegrationSettingsRestController.Payload(configuration, secrets, clearSecrets));
  }

  @Override
  public ConnectionTestResult test(
      IntegrationType type,
      String pluginId,
      Map<String, String> configuration,
      Map<String, String> secrets,
      Set<String> clearSecrets) {
    return rest.test(
        type,
        pluginId,
        new IntegrationSettingsRestController.Payload(configuration, secrets, clearSecrets));
  }

  @Override
  public IntegrationSettingsView setEnabled(
      IntegrationType type, String pluginId, boolean enabled) {
    return rest.enabled(type, pluginId, new IntegrationSettingsRestController.Enabled(enabled));
  }

  @Override
  public IntegrationSettingsView.JobView saveJob(
      IntegrationType type,
      String pluginId,
      String jobType,
      boolean enabled,
      String cron,
      String timezone) {
    return rest.job(
        type,
        pluginId,
        jobType,
        new IntegrationSettingsRestController.Job(enabled, cron, timezone));
  }

  @Override
  public void runJobNow(IntegrationType type, String pluginId, String jobType) {
    rest.runNow(type, pluginId, jobType);
  }
}
