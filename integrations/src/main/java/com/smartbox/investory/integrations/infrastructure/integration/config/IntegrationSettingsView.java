package com.smartbox.investory.integrations.infrastructure.integration.config;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationJobDescriptor;
import com.smartbox.investory.integrations.infrastructure.integration.IntegrationType;
import com.smartbox.investory.integrations.infrastructure.integration.PluginFieldDescriptor;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record IntegrationSettingsView(
    String pluginId,
    String name,
    IntegrationType type,
    String description,
    List<PluginFieldDescriptor> fields,
    Map<String, String> values,
    Map<String, SecretState> secrets,
    boolean enabled,
    List<IntegrationJobDescriptor> availableJobs,
    List<JobView> jobs) {
  public IntegrationSettingsView {
    fields = fields == null ? List.of() : List.copyOf(fields);
    values = values == null ? Map.of() : Map.copyOf(values);
    secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    availableJobs = availableJobs == null ? List.of() : List.copyOf(availableJobs);
    jobs = jobs == null ? List.of() : List.copyOf(jobs);
  }

  public record SecretState(boolean configured) {}

  public record JobView(
      String jobType,
      boolean enabled,
      String cron,
      String timezone,
      ZonedDateTime lastExecution,
      String status,
      String lastError) {}
}
