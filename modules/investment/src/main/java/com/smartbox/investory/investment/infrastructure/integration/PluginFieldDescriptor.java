package com.smartbox.investory.investment.infrastructure.integration;

import java.util.List;

public record PluginFieldDescriptor(
    String key,
    PluginFieldType type,
    boolean required,
    String defaultValue,
    List<String> allowedValues,
    String label,
    String description,
    Integer minimum,
    Integer maximum,
    String pattern) {
  public PluginFieldDescriptor(
      String key,
      PluginFieldType type,
      boolean required,
      String defaultValue,
      List<String> allowedValues) {
    this(key, type, required, defaultValue, allowedValues, key, null, null, null, null);
  }

  public PluginFieldDescriptor {
    allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    label = label == null || label.isBlank() ? key : label;
  }

  public static PluginFieldDescriptor requiredSecret(String key) {
    return new PluginFieldDescriptor(
        key, PluginFieldType.SECRET, true, null, List.of(), key, null, null, null, null);
  }
}
