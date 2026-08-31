package com.smartbox.investory.integrations.management.api.model;

import java.util.Map;
import java.util.Set;

public record IntegrationSettingsCommand(
    IntegrationType type,
    String pluginId,
    Map<String, String> configuration,
    Map<String, String> secrets,
    Set<String> clearSecrets) {
  public IntegrationSettingsCommand {
    configuration = configuration == null ? Map.of() : Map.copyOf(configuration);
    secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    clearSecrets = clearSecrets == null ? Set.of() : Set.copyOf(clearSecrets);
  }
}
