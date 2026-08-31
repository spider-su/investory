package com.smartbox.investory.integrations.infrastructure.integration;

public interface TestableIntegrationPlugin extends IntegrationPlugin {
  ConnectionTestResult testConnection(PluginConfig config);
}
