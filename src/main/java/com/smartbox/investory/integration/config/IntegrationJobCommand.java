package com.smartbox.investory.integration.config;

import com.smartbox.investory.integration.IntegrationType;
import java.util.Map;

public record IntegrationJobCommand(
    IntegrationType type,
    String pluginId,
    String jobType,
    boolean enabled,
    String cron,
    String timezone,
    Map<String, String> parameters) {
  public IntegrationJobCommand {
    parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
  }
}
