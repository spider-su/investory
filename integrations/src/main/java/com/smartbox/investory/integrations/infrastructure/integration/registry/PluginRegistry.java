package com.smartbox.investory.integrations.infrastructure.integration.registry;

import com.smartbox.investory.integrations.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.integrations.infrastructure.integration.IntegrationType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PluginRegistry {
  private final Map<IntegrationType, Map<String, IntegrationPlugin>> plugins;

  public PluginRegistry(List<IntegrationPlugin> discoveredPlugins) {
    Map<IntegrationType, Map<String, IntegrationPlugin>> byType =
        new EnumMap<>(IntegrationType.class);
    for (IntegrationPlugin plugin : discoveredPlugins) {
      Map<String, IntegrationPlugin> byId =
          byType.computeIfAbsent(plugin.type(), ignored -> new java.util.LinkedHashMap<>());
      IntegrationPlugin previous = byId.putIfAbsent(plugin.id(), plugin);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate integration plugin: " + plugin.type() + "/" + plugin.id());
      }
      if (!plugin.id().equals(plugin.descriptor().id())
          || plugin.type() != plugin.descriptor().type()) {
        throw new IllegalStateException("Plugin descriptor mismatch: " + plugin.id());
      }
    }
    plugins = byType;
  }

  public Optional<IntegrationPlugin> find(IntegrationType type, String id) {
    return Optional.ofNullable(plugins.getOrDefault(type, Map.of()).get(id));
  }

  public <T extends IntegrationPlugin> Optional<T> find(
      IntegrationType type, String id, Class<T> pluginType) {
    return find(type, id).filter(pluginType::isInstance).map(pluginType::cast);
  }

  public List<IntegrationPlugin> all() {
    return plugins.values().stream().flatMap(map -> map.values().stream()).toList();
  }
}
