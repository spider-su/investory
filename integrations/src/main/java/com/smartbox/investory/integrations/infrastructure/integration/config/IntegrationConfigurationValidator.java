package com.smartbox.investory.integrations.infrastructure.integration.config;

import com.smartbox.investory.integrations.infrastructure.integration.PluginDescriptor;
import com.smartbox.investory.integrations.infrastructure.integration.PluginFieldDescriptor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IntegrationConfigurationValidator {
  private IntegrationConfigurationValidator() {}

  public static List<FieldError> validate(PluginDescriptor descriptor, Map<String, String> values) {
    List<FieldError> errors = new ArrayList<>();
    Map<String, String> submitted = values == null ? Map.of() : values;
    for (String key : submitted.keySet()) {
      if (descriptor.configuration().stream().noneMatch(field -> field.key().equals(key))) {
        errors.add(new FieldError(key, "Unknown configuration field"));
      }
    }
    for (PluginFieldDescriptor field : descriptor.configuration()) {
      String value = submitted.get(field.key());
      if ((value == null || value.isBlank()) && field.required() && field.defaultValue() == null) {
        errors.add(new FieldError(field.key(), "Required field is missing"));
        continue;
      }
      if (value == null || value.isBlank()) continue;
      try {
        switch (field.type()) {
          case INTEGER -> {
            int parsed = Integer.parseInt(value);
            if (field.minimum() != null && parsed < field.minimum())
              errors.add(new FieldError(field.key(), "Must be at least " + field.minimum()));
            if (field.maximum() != null && parsed > field.maximum())
              errors.add(new FieldError(field.key(), "Must be at most " + field.maximum()));
          }
          case BOOLEAN -> {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
              errors.add(new FieldError(field.key(), "Must be true or false"));
          }
          case DURATION -> Duration.parse(value);
          case ENUM -> {
            if (!field.allowedValues().contains(value))
              errors.add(new FieldError(field.key(), "Unsupported value"));
          }
          default -> {}
        }
        if (field.pattern() != null && !value.matches(field.pattern()))
          errors.add(new FieldError(field.key(), "Invalid format"));
      } catch (RuntimeException exception) {
        errors.add(new FieldError(field.key(), "Invalid " + field.type().name().toLowerCase()));
      }
    }
    return List.copyOf(errors);
  }

  public record FieldError(String field, String message) {}
}
