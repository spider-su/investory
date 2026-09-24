package com.smartbox.investory.integrations.management.application;

import static com.smartbox.investory.integrations.FixedTestTime.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationSecretEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationSecretRepository;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class IntegrationConfigurationServiceRuntimeTest {
  private final IntegrationInstanceRepository instances = mock();
  private final IntegrationSecretRepository secrets = mock();
  private final PluginRegistry registry = mock();
  private final IntegrationSecretCipher cipher = mock();
  private final IntegrationConfigurationService service =
      new IntegrationConfigurationService(
          instances, secrets, registry, cipher, new ObjectMapper(), TIME);

  @Test
  void disabledPersistedInstanceShadowsEnvironmentFallback() {
    IntegrationInstanceEntity instance = instance(false);
    when(instances.findByOwnerIdAndPluginIdAndPluginType(null, "openai", IntegrationType.AI))
        .thenReturn(Optional.of(instance));

    assertThat(
            service
                .resolveForRuntime(
                    IntegrationType.AI, "openai", PluginConfig.of("apiKey", "environment"))
                .values())
        .isEmpty();
    verifyNoInteractions(secrets);
  }

  @Test
  void missingPersistedInstanceUsesExplicitFallback() {
    when(instances.findByOwnerIdAndPluginIdAndPluginType(null, "openai", IntegrationType.AI))
        .thenReturn(Optional.empty());

    assertThat(
            service
                .resolveForRuntime(
                    IntegrationType.AI, "openai", PluginConfig.of("apiKey", "environment"))
                .value("apiKey"))
        .contains("environment");
  }

  @Test
  void enabledPersistedInstanceResolvesEncryptedSecrets() {
    IntegrationInstanceEntity instance = instance(true);
    instance.setConfigJson("{\"baseUrl\":\"https://stored.example\"}");
    IntegrationSecretEntity secret = new IntegrationSecretEntity();
    secret.setSecretName("apiKey");
    secret.setCiphertext("ciphertext");
    when(instances.findByOwnerIdAndPluginIdAndPluginType(null, "openai", IntegrationType.AI))
        .thenReturn(Optional.of(instance));
    when(secrets.findByIntegrationInstanceId(11L)).thenReturn(java.util.List.of(secret));
    when(cipher.decrypt("ciphertext")).thenReturn("stored-secret");

    PluginConfig resolved =
        service.resolveForRuntime(IntegrationType.AI, "openai", PluginConfig.empty());

    assertThat(resolved.values())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("baseUrl", "https://stored.example", "apiKey", "stored-secret"));
  }

  @Test
  void replacingSecretDoesNotRequireDecryptingOldCiphertext() {
    IntegrationInstanceEntity instance = instance(false);
    instance.setPluginId("test");
    instance.setPluginType(IntegrationType.AI);
    IntegrationSecretEntity oldSecret = new IntegrationSecretEntity();
    oldSecret.setId(21L);
    oldSecret.setIntegrationInstanceId(11L);
    oldSecret.setSecretName("apiKey");
    oldSecret.setCiphertext("old-ciphertext");
    IntegrationPlugin plugin =
        new IntegrationPlugin() {
          @Override
          public String id() {
            return "test";
          }

          @Override
          public IntegrationType type() {
            return IntegrationType.AI;
          }

          @Override
          public com.smartbox.investory.integrations.management.model.PluginDescriptor
              descriptor() {
            return new com.smartbox.investory.integrations.management.model.PluginDescriptor(
                "test",
                "Test",
                IntegrationType.AI,
                List.of(
                    com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor
                        .requiredSecret("apiKey")),
                List.of());
          }

          @Override
          public com.smartbox.investory.integrations.management.model.ValidationResult validate(
              PluginConfig config) {
            return com.smartbox.investory.integrations.management.model.ValidationResult.success();
          }
        };
    when(registry.find(IntegrationType.AI, "test")).thenReturn(Optional.of(plugin));
    when(instances.findByOwnerIdAndPluginIdAndPluginType(null, "test", IntegrationType.AI))
        .thenReturn(Optional.of(instance));
    when(secrets.findByIntegrationInstanceId(11L)).thenReturn(List.of(oldSecret));
    when(secrets.findByIntegrationInstanceIdAndSecretName(11L, "apiKey"))
        .thenReturn(Optional.of(oldSecret));
    when(instances.save(instance)).thenReturn(instance);
    when(cipher.encrypt("new-secret")).thenReturn("new-ciphertext");

    service.saveGlobal("test", IntegrationType.AI, Map.of(), Map.of("apiKey", "new-secret"));

    verify(cipher, never()).decrypt("old-ciphertext");
    verify(cipher).encrypt("new-secret");
    assertThat(oldSecret.getCiphertext()).isEqualTo("new-ciphertext");
  }

  private static IntegrationInstanceEntity instance(boolean enabled) {
    IntegrationInstanceEntity instance = new IntegrationInstanceEntity();
    instance.setId(11L);
    instance.setEnabled(enabled);
    instance.setConfigJson("{}");
    return instance;
  }
}
