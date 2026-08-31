package com.smartbox.investory.integrations.management.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Plugin Registry")
class PluginRegistryTest {
  @DisplayName("resolves Plugins By Type And Id")
  @Test
  void resolvesPluginsByTypeAndId() {
    IntegrationPlugin plugin = plugin("one", IntegrationType.FX_DATA);

    PluginRegistry registry = new PluginRegistry(List.of(plugin));

    assertEquals(plugin, registry.find(IntegrationType.FX_DATA, "one").orElseThrow());
    assertEquals(1, registry.all().size());
  }

  @DisplayName("rejects Duplicate Plugin Ids Within Type")
  @Test
  void rejectsDuplicatePluginIdsWithinType() {
    IntegrationPlugin first = plugin("same", IntegrationType.FX_DATA);
    IntegrationPlugin second = plugin("same", IntegrationType.FX_DATA);

    assertThrows(IllegalStateException.class, () -> new PluginRegistry(List.of(first, second)));
  }

  @Test
  void rejectsJobsWithoutAnExecutableHandler() {
    IntegrationPlugin plugin =
        new IntegrationPlugin() {
          @Override
          public String id() {
            return "future";
          }

          @Override
          public IntegrationType type() {
            return IntegrationType.AI;
          }

          @Override
          public PluginDescriptor descriptor() {
            return new PluginDescriptor(id(), id(), type(), List.of(), List.of("future-job"));
          }

          @Override
          public ValidationResult validate(PluginConfig config) {
            return ValidationResult.success();
          }
        };

    assertThrows(IllegalStateException.class, () -> new PluginRegistry(List.of(plugin)));
  }

  private static IntegrationPlugin plugin(String id, IntegrationType type) {
    return new IntegrationPlugin() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public IntegrationType type() {
        return type;
      }

      @Override
      public PluginDescriptor descriptor() {
        return new PluginDescriptor(id, id, type, List.of(), List.of());
      }

      @Override
      public ValidationResult validate(PluginConfig config) {
        return ValidationResult.success();
      }
    };
  }
}
