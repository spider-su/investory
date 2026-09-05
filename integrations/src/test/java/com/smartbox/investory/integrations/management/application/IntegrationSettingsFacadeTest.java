package com.smartbox.investory.integrations.management.application;

import static com.smartbox.investory.integrations.FixedTestTime.TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.api.model.PluginFieldDescriptor;
import com.smartbox.investory.integrations.management.api.model.PluginFieldType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.PluginDescriptor;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationSecretEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationSecretRepository;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandlerRegistry;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import com.smartbox.investory.integrations.management.spi.TestableIntegrationPlugin;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Integration Settings Facade")
class IntegrationSettingsFacadeTest {

  @DisplayName("transient Connection Test Does Not Replace Saved Diagnostic")
  @Test
  void transientConnectionTestDoesNotReplaceSavedDiagnostic() {
    var context = testContext(Map.of());

    ConnectionTestResult result =
        context.facade.testConnection(
            new IntegrationSettingsCommand(
                IntegrationType.FX_DATA,
                "testable",
                Map.of("url", "https://draft"),
                Map.of(),
                Set.of()));

    assertThat(result.success()).isTrue();
    verify(context.recorder, never())
        .record(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString());
  }

  @DisplayName("saved Configuration Test Updates Saved Diagnostic")
  @Test
  void savedConfigurationTestUpdatesSavedDiagnostic() {
    var context = testContext(Map.of("url", "https://saved"));

    ConnectionTestResult result =
        context.facade.testConnection(
            new IntegrationSettingsCommand(
                IntegrationType.FX_DATA, "testable", Map.of(), Map.of(), Set.of()));

    assertThat(result.success()).isTrue();
    verify(context.recorder).record(IntegrationType.FX_DATA, "testable", result, result.message());
  }

  @DisplayName("timeout during transient test Does Not Persist Failure")
  @Test
  void timeoutDuringTransientTestDoesNotPersistFailure() {
    var context = testContext(Map.of(), true);

    ConnectionTestResult result =
        context.facade.testConnection(
            new IntegrationSettingsCommand(
                IntegrationType.FX_DATA,
                "testable",
                Map.of("url", "https://draft"),
                Map.of(),
                Set.of()));

    assertThat(result.success()).isFalse();
    assertThat(result.message()).contains("Connection test failed");
    verify(context.recorder, never())
        .record(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString());
  }

  private static TestContext testContext(Map<String, String> savedValues) {
    return testContext(savedValues, false);
  }

  private static TestContext testContext(
      Map<String, String> savedValues, boolean throwConnectionFailure) {
    TestableIntegrationPlugin plugin =
        new TestableIntegrationPlugin() {
          public String id() {
            return "testable";
          }

          public IntegrationType type() {
            return IntegrationType.FX_DATA;
          }

          public PluginDescriptor descriptor() {
            return new PluginDescriptor(
                "testable",
                "Testable",
                type(),
                List.of(
                    new PluginFieldDescriptor("url", PluginFieldType.URL, false, null, List.of())),
                List.of());
          }

          public ValidationResult validate(PluginConfig config) {
            return ValidationResult.success();
          }

          public ConnectionTestResult testConnection(PluginConfig config) {
            if (throwConnectionFailure) throw new IllegalStateException("timeout");
            return new ConnectionTestResult(true, true, "ok");
          }
        };
    var instanceRepository = mock(IntegrationInstanceRepository.class);
    var instance = new IntegrationInstanceEntity();
    instance.setId(9L);
    instance.setPluginId(plugin.id());
    instance.setPluginType(plugin.type());
    instance.setConfigJson("{}");
    when(instanceRepository.findByOwnerIdAndPluginIdAndPluginType(null, plugin.id(), plugin.type()))
        .thenReturn(Optional.of(instance));
    var configurationService = mock(IntegrationConfigurationService.class);
    when(configurationService.resolve(instance)).thenReturn(new PluginConfig(savedValues));
    var recorder = mock(IntegrationTestResultRecorder.class);
    var facade =
        new IntegrationSettingsFacade(
            new PluginRegistry(List.of(plugin)),
            mock(IntegrationJobHandlerRegistry.class),
            configurationService,
            instanceRepository,
            mock(IntegrationSecretRepository.class),
            mock(IntegrationJobRepository.class),
            mock(IntegrationSecretCipher.class),
            new ObjectMapper(),
            recorder,
            TIME);
    return new TestContext(facade, recorder);
  }

  private record TestContext(
      IntegrationSettingsFacade facade, IntegrationTestResultRecorder recorder) {}

  @DisplayName("settings View Exposes Secret State But Never Secret Plaintext")
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
    var instance = new IntegrationInstanceEntity();
    instance.setId(7L);
    instance.setPluginId("test");
    instance.setPluginType(IntegrationType.FX_DATA);
    instance.setConfigJson("{\"region\":\"eu\"}");
    when(instances.findByOwnerIdAndPluginIdAndPluginType(null, "test", IntegrationType.FX_DATA))
        .thenReturn(Optional.of(instance));
    var secret = new IntegrationSecretEntity();
    secret.setSecretName("apiKey");
    when(secrets.findByIntegrationInstanceId(7L)).thenReturn(List.of(secret));
    when(jobs.findByIntegrationInstanceId(7L)).thenReturn(List.of());
    var facade =
        new IntegrationSettingsFacade(
            registry,
            mock(IntegrationJobHandlerRegistry.class),
            config,
            instances,
            secrets,
            jobs,
            cipher,
            new ObjectMapper(),
            mock(IntegrationTestResultRecorder.class),
            TIME);

    var view = facade.getIntegration(IntegrationType.FX_DATA, "test");

    assertThat(view.values()).doesNotContainKey("apiKey");
    assertThat(view.secrets().get("apiKey").configured()).isTrue();
  }
}
