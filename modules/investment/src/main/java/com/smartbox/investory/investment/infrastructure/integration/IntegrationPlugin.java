package com.smartbox.investory.investment.infrastructure.integration;

public interface IntegrationPlugin {
  String id();

  IntegrationType type();

  PluginDescriptor descriptor();

  ValidationResult validate(PluginConfig config);
}
