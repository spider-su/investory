package com.smartbox.investory.integrations.management.spi;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;

public interface IntegrationPlugin {
  String id();

  IntegrationType type();

  PluginDescriptor descriptor();

  ValidationResult validate(PluginConfig config);
}
