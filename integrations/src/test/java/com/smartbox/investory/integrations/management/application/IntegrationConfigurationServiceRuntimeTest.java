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

  private static IntegrationInstanceEntity instance(boolean enabled) {
    IntegrationInstanceEntity instance = new IntegrationInstanceEntity();
    instance.setId(11L);
    instance.setEnabled(enabled);
    instance.setConfigJson("{}");
    return instance;
  }
}
