package com.smartbox.investory.integrations.management.application;

import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IntegrationConfigurationValidator {
  private IntegrationConfigurationValidator() {}

  public static List<FieldError> validate(PluginDescriptor descriptor, Map<String, String> values) {
    return validate(descriptor, values, false);
  }

  public static List<FieldError> validate(
      PluginDescriptor descriptor, Map<String, String> values, boolean allowPrivateBaseUrls) {
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
          case URL -> {
            java.net.URI uri = java.net.URI.create(value);
            if (uri.getScheme() == null
                || uri.getHost() == null
                || (!"https".equalsIgnoreCase(uri.getScheme())
                    && !(allowPrivateBaseUrls && "http".equalsIgnoreCase(uri.getScheme())))
                || (!allowPrivateBaseUrls && isPrivateHost(uri.getHost())))
              errors.add(new FieldError(field.key(), "Must be a valid HTTPS URL"));
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

  private static boolean isPrivateHost(String host) {
    String normalized = host.toLowerCase(java.util.Locale.ROOT);
    if (normalized.equals("localhost") || normalized.endsWith(".localhost")) return true;
    if (normalized.equals("::1") || normalized.startsWith("127.")) return true;
    if (normalized.startsWith("10.") || normalized.startsWith("192.168.")) return true;
    if (normalized.startsWith("172.")) {
      try {
        int end = normalized.indexOf('.', 4);
        int second = Integer.parseInt(normalized.substring(4, end));
        return second >= 16 && second <= 31;
      } catch (RuntimeException ignored) {
        return false;
      }
    }
    return false;
  }

  public record FieldError(String field, String message) {}
}
