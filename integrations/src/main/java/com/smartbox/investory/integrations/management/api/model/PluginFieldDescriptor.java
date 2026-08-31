package com.smartbox.investory.integrations.management.api.model;

import java.util.List;
import java.util.Locale;

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
    label = label == null || label.isBlank() ? humanize(key) : label;
  }

  public static PluginFieldDescriptor requiredSecret(String key) {
    return new PluginFieldDescriptor(
        key, PluginFieldType.SECRET, true, null, List.of(), null, null, null, null, null);
  }

  private static String humanize(String key) {
    if (key == null || key.isBlank()) return "Field";
    String text = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
    if (text.equalsIgnoreCase("api key")) return "API key";
    return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
  }
}
