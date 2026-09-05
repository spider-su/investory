package com.smartbox.investory.integrations.management.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.PluginRegistry;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationJobHandlerRegistryTest {
  @Test
  void resolvesDeclaredHandler() {
    IntegrationJobHandler handler = handler(IntegrationType.MARKET_DATA, "refresh-prices");
    PluginRegistry plugins =
        new PluginRegistry(List.of(plugin(IntegrationType.MARKET_DATA, "refresh-prices")));

    assertThat(
            new IntegrationJobHandlerRegistry(List.of(handler), plugins)
                .require(IntegrationType.MARKET_DATA, "refresh-prices"))
        .isSameAs(handler);
  }

  @Test
  void rejectsDuplicateHandlers() {
    IntegrationJobHandler first = handler(IntegrationType.MARKET_DATA, "refresh-prices");
    IntegrationJobHandler second = handler(IntegrationType.MARKET_DATA, "refresh-prices");
    PluginRegistry plugins = new PluginRegistry(List.of());

    assertThatThrownBy(() -> new IntegrationJobHandlerRegistry(List.of(first, second), plugins))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  @Test
  void rejectsDeclaredJobWithoutHandler() {
    PluginRegistry plugins = new PluginRegistry(List.of(plugin(IntegrationType.AI, "future-job")));

    assertThatThrownBy(() -> new IntegrationJobHandlerRegistry(List.of(), plugins))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no executable handler");
  }

  private static IntegrationJobHandler handler(IntegrationType type, String jobType) {
    IntegrationJobHandler handler = mock(IntegrationJobHandler.class);
    when(handler.integrationType()).thenReturn(type);
    when(handler.jobType()).thenReturn(jobType);
    return handler;
  }

  private static IntegrationPlugin plugin(IntegrationType type, String jobType) {
    return new IntegrationPlugin() {
      public String id() {
        return "test-" + jobType;
      }

      public IntegrationType type() {
        return type;
      }

      public PluginDescriptor descriptor() {
        return new PluginDescriptor(id(), id(), type(), List.of(), List.of(jobType));
      }

      public ValidationResult validate(PluginConfig config) {
        return ValidationResult.success();
      }
    };
  }
}
