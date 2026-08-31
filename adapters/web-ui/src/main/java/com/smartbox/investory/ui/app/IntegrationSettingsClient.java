package com.smartbox.investory.ui.app;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Replaceable UI seam for integration-management commands. */
public interface IntegrationSettingsClient {
  List<IntegrationSettingsView> list();

  IntegrationSettingsView save(
      IntegrationType type,
      String pluginId,
      Map<String, String> configuration,
      Map<String, String> secrets,
      Set<String> clearSecrets);

  ConnectionTestResult test(
      IntegrationType type,
      String pluginId,
      Map<String, String> configuration,
      Map<String, String> secrets,
      Set<String> clearSecrets);

  IntegrationSettingsView setEnabled(IntegrationType type, String pluginId, boolean enabled);

  IntegrationSettingsView.JobView saveJob(
      IntegrationType type,
      String pluginId,
      String jobType,
      boolean enabled,
      String cron,
      String timezone);
}
