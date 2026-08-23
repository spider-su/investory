package com.smartbox.investory.investment.infrastructure.integration;

public interface TestableIntegrationPlugin extends IntegrationPlugin {
  ConnectionTestResult testConnection(PluginConfig config);
}
