package com.smartbox.investory.integrations.management.spi;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.model.PluginConfig;

public interface TestableIntegrationPlugin extends IntegrationPlugin {
  ConnectionTestResult testConnection(PluginConfig config);
}
