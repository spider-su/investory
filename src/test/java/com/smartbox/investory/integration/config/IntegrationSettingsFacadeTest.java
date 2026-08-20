package com.smartbox.investory.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integration.IntegrationPlugin;
import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.PluginConfig;
import com.smartbox.investory.integration.PluginDescriptor;
import com.smartbox.investory.integration.PluginFieldDescriptor;
import com.smartbox.investory.integration.ValidationResult;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationInstance;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationJobRepository;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationSecret;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationSecretRepository;
import com.smartbox.investory.integration.registry.PluginRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntegrationSettingsFacadeTest {
  @Test
  void settingsViewExposesSecretStateButNeverSecretPlaintext() {
    IntegrationPlugin plugin =
        new IntegrationPlugin() {
          public String id() {
            return "test";
          }

          public IntegrationType type() {
            return IntegrationType.FX_DATA;
          }

          public PluginDescriptor descriptor() {
            return new PluginDescriptor(
                "test",
                "Test",
                type(),
                List.of(PluginFieldDescriptor.requiredSecret("apiKey")),
                List.of());
          }

          public ValidationResult validate(PluginConfig config) {
            return ValidationResult.success();
          }
        };
    var registry = new PluginRegistry(List.of(plugin));
    var instances = mock(IntegrationInstanceRepository.class);
    var secrets = mock(IntegrationSecretRepository.class);
    var jobs = mock(IntegrationJobRepository.class);
    var config = mock(IntegrationConfigurationService.class);
    var cipher = mock(IntegrationSecretCipher.class);
    var instance = new IntegrationInstance();
    instance.setId(7L);
    instance.setPluginId("test");
    instance.setPluginType(IntegrationType.FX_DATA);
    instance.setConfigJson("{\"region\":\"eu\"}");
    when(instances.findByOwnerIdAndPluginIdAndPluginType(null, "test", IntegrationType.FX_DATA))
        .thenReturn(Optional.of(instance));
    var secret = new IntegrationSecret();
    secret.setSecretName("apiKey");
    when(secrets.findByIntegrationInstanceId(7L)).thenReturn(List.of(secret));
    when(jobs.findByIntegrationInstanceId(7L)).thenReturn(List.of());
    var facade = new IntegrationSettingsFacade(registry, config, instances, secrets, jobs, cipher);

    var view = facade.getIntegration(IntegrationType.FX_DATA, "test");

    assertThat(view.values()).doesNotContainKey("apiKey");
    assertThat(view.secrets().get("apiKey").configured()).isTrue();
  }
}
