package com.smartbox.investory.investment.infrastructure.integration.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.smartbox.investory.investment.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.investment.infrastructure.integration.IntegrationType;
import com.smartbox.investory.investment.infrastructure.integration.PluginConfig;
import com.smartbox.investory.investment.infrastructure.integration.PluginDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.ValidationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginRegistryTest {
  @Test
  void resolvesPluginsByTypeAndId() {
    IntegrationPlugin plugin = plugin("one", IntegrationType.FX_DATA);

    PluginRegistry registry = new PluginRegistry(List.of(plugin));

    assertEquals(plugin, registry.find(IntegrationType.FX_DATA, "one").orElseThrow());
    assertEquals(1, registry.all().size());
  }

  @Test
  void rejectsDuplicatePluginIdsWithinType() {
    IntegrationPlugin first = plugin("same", IntegrationType.FX_DATA);
    IntegrationPlugin second = plugin("same", IntegrationType.FX_DATA);

    assertThrows(IllegalStateException.class, () -> new PluginRegistry(List.of(first, second)));
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
