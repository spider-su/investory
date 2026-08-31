package com.smartbox.investory.integrations.management.api;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationJobCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import java.util.List;

/** Public in-process boundary for integration settings management. */
public interface IntegrationSettingsApi {
  List<IntegrationSettingsView> listIntegrations();

  IntegrationSettingsView saveConfiguration(IntegrationSettingsCommand command);

  ConnectionTestResult testConnection(IntegrationSettingsCommand command);

  IntegrationSettingsView setEnabled(IntegrationType type, String pluginId, boolean enabled);

  IntegrationSettingsView.JobView saveJob(IntegrationJobCommand command);
}
