package com.smartbox.investory.integration;

public interface TestableIntegrationPlugin extends IntegrationPlugin {
  ConnectionTestResult testConnection(PluginConfig config);
}
