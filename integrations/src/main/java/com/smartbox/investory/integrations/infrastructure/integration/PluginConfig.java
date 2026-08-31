package com.smartbox.investory.integrations.infrastructure.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record PluginConfig(Map<String, String> values) {
  public PluginConfig {
    values = values == null ? Map.of() : Map.copyOf(values);
  }

  public static PluginConfig of(String key, String value) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(key, value);
    return new PluginConfig(values);
  }

  public static PluginConfig empty() {
    return new PluginConfig(Map.of());
  }

  public Optional<String> value(String key) {
    return Optional.ofNullable(values.get(key)).filter(value -> !value.isBlank());
  }
}
