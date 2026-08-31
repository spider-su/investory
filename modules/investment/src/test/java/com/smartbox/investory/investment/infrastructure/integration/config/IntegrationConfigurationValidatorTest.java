package com.smartbox.investory.investment.infrastructure.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.investment.infrastructure.integration.IntegrationType;
import com.smartbox.investory.investment.infrastructure.integration.PluginDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.PluginFieldDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.PluginFieldType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntegrationConfigurationValidatorTest {
  private final PluginDescriptor descriptor =
      new PluginDescriptor(
          "test",
          "Test",
          IntegrationType.FX_DATA,
          List.of(
              new PluginFieldDescriptor("enabled", PluginFieldType.BOOLEAN, true, null, List.of()),
              new PluginFieldDescriptor("count", PluginFieldType.INTEGER, true, null, List.of()),
              new PluginFieldDescriptor(
                  "mode", PluginFieldType.ENUM, true, null, List.of("A", "B")),
              new PluginFieldDescriptor(
                  "timeout", PluginFieldType.DURATION, false, "PT10S", List.of())),
          List.of());

  @Test
  void rejectsUnknownFieldsAndInvalidTypedValues() {
    var errors =
        IntegrationConfigurationValidator.validate(
            descriptor, Map.of("enabled", "yes", "count", "x", "mode", "C", "unknown", "value"));

    assertThat(errors)
        .extracting(IntegrationConfigurationValidator.FieldError::field)
        .containsExactlyInAnyOrder("enabled", "count", "mode", "unknown");
  }

  @Test
  void acceptsDefaultsAndValidTypedValues() {
    assertThat(
            IntegrationConfigurationValidator.validate(
                descriptor, Map.of("enabled", "true", "count", "2", "mode", "A")))
        .isEmpty();
  }
}
