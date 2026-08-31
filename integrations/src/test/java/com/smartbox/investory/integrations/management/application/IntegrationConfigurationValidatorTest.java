package com.smartbox.investory.integrations.management.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.api.model.PluginFieldType;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Integration Configuration Validator")
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

  @DisplayName("rejects Unknown Fields And Invalid Typed Values")
  @Test
  void rejectsUnknownFieldsAndInvalidTypedValues() {
    var errors =
        IntegrationConfigurationValidator.validate(
            descriptor, Map.of("enabled", "yes", "count", "x", "mode", "C", "unknown", "value"));

    assertThat(errors)
        .extracting(IntegrationConfigurationValidator.FieldError::field)
        .containsExactlyInAnyOrder("enabled", "count", "mode", "unknown");
  }

  @DisplayName("accepts Defaults And Valid Typed Values")
  @Test
  void acceptsDefaultsAndValidTypedValues() {
    assertThat(
            IntegrationConfigurationValidator.validate(
                descriptor, Map.of("enabled", "true", "count", "2", "mode", "A")))
        .isEmpty();
  }

  @Test
  void rejectsInsecureAndPrivateBaseUrlsByDefault() {
    PluginDescriptor urls =
        new PluginDescriptor(
            "urls",
            "URLs",
            IntegrationType.AI,
            List.of(
                new PluginFieldDescriptor("baseUrl", PluginFieldType.URL, true, null, List.of())),
            List.of());

    for (String privateUrl :
        List.of("http://127.0.0.1:8080", "http://169.254.1.1", "http://0.0.0.0")) {
      assertThat(IntegrationConfigurationValidator.validate(urls, Map.of("baseUrl", privateUrl)))
          .extracting(IntegrationConfigurationValidator.FieldError::field)
          .containsExactly("baseUrl");
    }
    assertThat(
            IntegrationConfigurationValidator.validate(
                urls, Map.of("baseUrl", "http://127.0.0.1:8080"), true))
        .isEmpty();
  }
}
