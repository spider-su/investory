package com.smartbox.investory.investment.imports;

import com.smartbox.investory.infrastructure.BrokerType;
import com.smartbox.investory.integration.IntegrationPlugin;
import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.PluginDescriptor;
import java.io.InputStream;

public interface BrokerImportParser extends IntegrationPlugin {
  BrokerType brokerType();

  ImportExecutionResult importFile(InputStream inputStream, String fileName) throws Exception;

  @Override
  default String id() {
    return brokerType().name().toLowerCase(java.util.Locale.ROOT);
  }

  @Override
  default IntegrationType type() {
    return IntegrationType.BROKER_IMPORT;
  }

  @Override
  default PluginDescriptor descriptor() {
    return new PluginDescriptor(
        id(), brokerType().name(), type(), java.util.List.of(), java.util.List.of());
  }

  @Override
  default com.smartbox.investory.integration.ValidationResult validate(
      com.smartbox.investory.integration.PluginConfig config) {
    return com.smartbox.investory.integration.ValidationResult.success();
  }
}
