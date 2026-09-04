package com.smartbox.investory.integrations.management.scheduling;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.PluginRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntegrationJobHandlerRegistry {
  private final Map<Key, IntegrationJobHandler> handlers;

  public IntegrationJobHandlerRegistry(
      List<IntegrationJobHandler> discoveredHandlers, PluginRegistry pluginRegistry) {
    Map<Key, IntegrationJobHandler> registered = new LinkedHashMap<>();
    for (IntegrationJobHandler handler : discoveredHandlers) {
      Key key = new Key(handler.integrationType(), handler.jobType());
      if (registered.putIfAbsent(key, handler) != null) {
        throw new IllegalStateException("Duplicate integration job handler: " + key);
      }
    }
    for (var plugin : pluginRegistry.all()) {
      for (var descriptor : plugin.descriptor().jobDescriptors()) {
        Key key = new Key(plugin.type(), descriptor.jobType());
        if (!registered.containsKey(key)) {
          throw new IllegalStateException("Integration job has no executable handler: " + key);
        }
      }
    }
    handlers = Map.copyOf(registered);
  }

  public IntegrationJobHandler require(IntegrationType type, String jobType) {
    IntegrationJobHandler handler = handlers.get(new Key(type, jobType));
    if (handler == null) {
      throw new IllegalArgumentException("Unsupported integration job: " + type + "/" + jobType);
    }
    return handler;
  }

  public boolean supports(IntegrationType type, String jobType) {
    return handlers.containsKey(new Key(type, jobType));
  }

  private record Key(IntegrationType integrationType, String jobType) {
    @Override
    public String toString() {
      return integrationType + "/" + jobType;
    }
  }
}
