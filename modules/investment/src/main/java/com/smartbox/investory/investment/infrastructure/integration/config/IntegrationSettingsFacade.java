package com.smartbox.investory.investment.infrastructure.integration.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.investment.infrastructure.integration.ConnectionTestResult;
import com.smartbox.investory.investment.infrastructure.integration.IntegrationJobDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.IntegrationPlugin;
import com.smartbox.investory.investment.infrastructure.integration.IntegrationType;
import com.smartbox.investory.investment.infrastructure.integration.PluginConfig;
import com.smartbox.investory.investment.infrastructure.integration.PluginFieldDescriptor;
import com.smartbox.investory.investment.infrastructure.integration.PluginFieldType;
import com.smartbox.investory.investment.infrastructure.integration.TestableIntegrationPlugin;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationInstance;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationJob;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationJobRepository;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationSecretRepository;
import com.smartbox.investory.investment.infrastructure.integration.registry.PluginRegistry;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationSettingsFacade {
  private final PluginRegistry pluginRegistry;
  private final IntegrationConfigurationService configurationService;
  private final IntegrationInstanceRepository instanceRepository;
  private final IntegrationSecretRepository secretRepository;
  private final IntegrationJobRepository jobRepository;
  private final IntegrationSecretCipher secretCipher;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Transactional(readOnly = true)
  public List<IntegrationSettingsView> listIntegrations() {
    return pluginRegistry.all().stream().map(plugin -> view(plugin, instance(plugin))).toList();
  }

  @Transactional(readOnly = true)
  public IntegrationSettingsView getIntegration(IntegrationType type, String pluginId) {
    IntegrationPlugin plugin = plugin(type, pluginId);
    return view(plugin, instance(plugin));
  }

  @Transactional
  public IntegrationSettingsView saveConfiguration(IntegrationSettingsCommand command) {
    IntegrationPlugin plugin = plugin(command.type(), command.pluginId());
    validateKeys(plugin, command);
    Map<String, String> effective = effective(plugin);
    effective.putAll(command.configuration());
    command
        .secrets()
        .forEach(
            (key, value) -> {
              if (value != null && !value.isBlank()) effective.put(key, value);
            });
    command.clearSecrets().forEach(effective::remove);
    effective.putAll(defaults(plugin, effective));
    List<IntegrationConfigurationValidator.FieldError> errors =
        IntegrationConfigurationValidator.validate(plugin.descriptor(), effective);
    if (!errors.isEmpty()) throw new IntegrationSettingsValidationException(errors);
    var pluginValidation = plugin.validate(new PluginConfig(effective));
    if (!pluginValidation.valid())
      throw new IntegrationSettingsValidationException(
          List.of(
              new IntegrationConfigurationValidator.FieldError(
                  "configuration", String.join("; ", pluginValidation.errors()))));
    Map<String, String> replacementSecrets =
        command.secrets().entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    Map<String, String> normalizedConfiguration =
        effective.entrySet().stream()
            .filter(entry -> !isSecretField(plugin, entry.getKey()))
            .collect(
                java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (left, right) -> right,
                    LinkedHashMap::new));
    configurationService.saveGlobal(
        command.pluginId(),
        command.type(),
        normalizedConfiguration,
        replacementSecrets,
        command.clearSecrets());
    return getIntegration(command.type(), command.pluginId());
  }

  @Transactional
  public IntegrationSettingsView setEnabled(
      IntegrationType type, String pluginId, boolean enabled) {
    IntegrationPlugin plugin = plugin(type, pluginId);
    IntegrationInstance instance = instance(plugin);
    if (instance == null) {
      if (enabled)
        throw new IntegrationSettingsValidationException(
            List.of(
                new IntegrationConfigurationValidator.FieldError(
                    "enabled", "Save a valid configuration first")));
      return view(plugin, null);
    }
    if (enabled) validateEffective(plugin, effective(plugin));
    instance.setEnabled(enabled);
    instance.setUpdatedAt(ZonedDateTime.now());
    instanceRepository.save(instance);
    return view(plugin, instance);
  }

  @Transactional
  public IntegrationSettingsView.JobView saveJob(IntegrationJobCommand command) {
    IntegrationPlugin plugin = plugin(command.type(), command.pluginId());
    IntegrationJobDescriptor descriptor =
        plugin.descriptor().jobDescriptors().stream()
            .filter(job -> job.jobType().equals(command.jobType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unsupported integration job: " + command.jobType()));
    String cron =
        command.cron() == null || command.cron().isBlank()
            ? descriptor.defaultCron()
            : command.cron();
    String timezone =
        command.timezone() == null || command.timezone().isBlank()
            ? descriptor.defaultTimezone()
            : command.timezone();
    validateSchedule(cron, timezone);
    if (!command.parameters().isEmpty())
      throw new IllegalArgumentException("Unsupported job parameters");
    IntegrationInstance instance = instance(plugin);
    if (instance == null)
      throw new IllegalArgumentException("Save integration configuration first");
    if (command.enabled()) {
      if (!instance.isEnabled())
        throw new IllegalArgumentException("Enable the integration before enabling a job");
      validateEffective(plugin, effective(plugin));
    }
    IntegrationJob job =
        jobRepository.findByIntegrationInstanceId(instance.getId()).stream()
            .filter(item -> item.getJobType().equals(command.jobType()))
            .findFirst()
            .orElseGet(IntegrationJob::new);
    job.setIntegrationInstanceId(instance.getId());
    job.setJobType(descriptor.jobType());
    job.setEnabled(command.enabled());
    job.setCron(cron);
    job.setTimezone(timezone);
    job.setParametersJson("{}");
    return job(jobRepository.save(job));
  }

  @Transactional
  public IntegrationSettingsView.JobView setJobEnabled(Long jobId, boolean enabled) {
    IntegrationJob job =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown integration job"));
    if (enabled) {
      validateSchedule(job.getCron(), job.getTimezone());
      IntegrationInstance instance =
          instanceRepository
              .findById(job.getIntegrationInstanceId())
              .orElseThrow(() -> new IllegalArgumentException("Unknown integration instance"));
      if (!instance.isEnabled())
        throw new IllegalArgumentException("Enable the integration before enabling a job");
      IntegrationPlugin plugin = plugin(instance.getPluginType(), instance.getPluginId());
      validateEffective(plugin, effective(plugin));
    }
    job.setEnabled(enabled);
    return job(jobRepository.save(job));
  }

  @Transactional(readOnly = true)
  public ConnectionTestResult testConnection(IntegrationSettingsCommand command) {
    IntegrationPlugin plugin = plugin(command.type(), command.pluginId());
    if (!(plugin instanceof TestableIntegrationPlugin testable))
      return ConnectionTestResult.unsupported();
    validateKeys(plugin, command);
    Map<String, String> effective = effective(plugin);
    effective.putAll(command.configuration());
    command
        .secrets()
        .forEach(
            (key, value) -> {
              if (value != null && !value.isBlank()) effective.put(key, value);
            });
    command.clearSecrets().forEach(effective::remove);
    effective.putAll(defaults(plugin, effective));
    validateEffective(plugin, effective);
    try {
      ConnectionTestResult result = testable.testConnection(new PluginConfig(effective));
      return new ConnectionTestResult(
          result.supported(), result.success(), sanitize(result.message()));
    } catch (RuntimeException exception) {
      return new ConnectionTestResult(true, false, "Connection test failed");
    }
  }

  private void validateKeys(IntegrationPlugin plugin, IntegrationSettingsCommand command) {
    Set<String> allowed =
        plugin.descriptor().configuration().stream()
            .map(PluginFieldDescriptor::key)
            .collect(java.util.stream.Collectors.toSet());
    List<IntegrationConfigurationValidator.FieldError> errors = new java.util.ArrayList<>();
    command.configuration().keySet().stream()
        .filter(Predicate.not(allowed::contains))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(
                        key, "Unknown configuration field")));
    command.configuration().keySet().stream()
        .filter(key -> isSecretField(plugin, key))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(
                        key, "Secret must be submitted through secrets")));
    command.secrets().keySet().stream()
        .filter(Predicate.not(allowed::contains))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(key, "Unknown secret field")));
    command.secrets().keySet().stream()
        .filter(allowed::contains)
        .filter(Predicate.not(key -> isSecretField(plugin, key)))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(key, "Field is not secret")));
    command.clearSecrets().stream()
        .filter(Predicate.not(allowed::contains))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(key, "Unknown secret field")));
    command.clearSecrets().stream()
        .filter(allowed::contains)
        .filter(Predicate.not(key -> isSecretField(plugin, key)))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(key, "Field is not secret")));
    command.clearSecrets().stream()
        .filter(
            key ->
                plugin.descriptor().configuration().stream()
                    .filter(field -> field.key().equals(key))
                    .findFirst()
                    .map(PluginFieldDescriptor::required)
                    .orElse(false))
        .forEach(
            key ->
                errors.add(
                    new IntegrationConfigurationValidator.FieldError(
                        key, "Required secret cannot be cleared")));
    if (!errors.isEmpty()) throw new IntegrationSettingsValidationException(errors);
  }

  private boolean isSecretField(IntegrationPlugin plugin, String key) {
    return plugin.descriptor().configuration().stream()
        .filter(field -> field.key().equals(key))
        .map(PluginFieldDescriptor::type)
        .anyMatch(type -> type == PluginFieldType.SECRET);
  }

  private void validateEffective(IntegrationPlugin plugin, Map<String, String> effective) {
    List<IntegrationConfigurationValidator.FieldError> errors =
        IntegrationConfigurationValidator.validate(plugin.descriptor(), effective);
    if (!errors.isEmpty()) throw new IntegrationSettingsValidationException(errors);
    var validation = plugin.validate(new PluginConfig(effective));
    if (!validation.valid())
      throw new IntegrationSettingsValidationException(
          List.of(
              new IntegrationConfigurationValidator.FieldError(
                  "configuration", String.join("; ", validation.errors()))));
  }

  private Map<String, String> effective(IntegrationPlugin plugin) {
    IntegrationInstance instance = instance(plugin);
    return instance == null
        ? new LinkedHashMap<>()
        : new LinkedHashMap<>(configurationService.resolve(instance).values());
  }

  private Map<String, String> defaults(IntegrationPlugin plugin, Map<String, String> values) {
    Map<String, String> result = new LinkedHashMap<>(values);
    plugin
        .descriptor()
        .configuration()
        .forEach(
            field -> {
              if (!result.containsKey(field.key()) && field.defaultValue() != null)
                result.put(field.key(), field.defaultValue());
            });
    return result;
  }

  private IntegrationSettingsView view(IntegrationPlugin plugin, IntegrationInstance instance) {
    Map<String, String> values = instance == null ? Map.of() : fromJson(instance.getConfigJson());
    Map<String, IntegrationSettingsView.SecretState> secrets = new LinkedHashMap<>();
    if (instance != null)
      secretRepository
          .findByIntegrationInstanceId(instance.getId())
          .forEach(
              secret ->
                  secrets.put(
                      secret.getSecretName(), new IntegrationSettingsView.SecretState(true)));
    plugin.descriptor().configuration().stream()
        .filter(field -> field.type() == PluginFieldType.SECRET)
        .forEach(
            field ->
                secrets.putIfAbsent(field.key(), new IntegrationSettingsView.SecretState(false)));
    List<IntegrationSettingsView.JobView> jobs =
        instance == null
            ? List.of()
            : jobRepository.findByIntegrationInstanceId(instance.getId()).stream()
                .map(this::job)
                .toList();
    return new IntegrationSettingsView(
        plugin.id(),
        plugin.descriptor().name(),
        plugin.type(),
        plugin.descriptor().description(),
        plugin.descriptor().configuration(),
        values,
        secrets,
        instance != null && instance.isEnabled(),
        plugin.descriptor().jobDescriptors(),
        jobs);
  }

  private IntegrationSettingsView.JobView job(IntegrationJob job) {
    return new IntegrationSettingsView.JobView(
        job.getJobType(),
        job.isEnabled(),
        job.getCron(),
        job.getTimezone(),
        job.getLastCompletedAt(),
        job.getLastStatus(),
        sanitize(job.getLastError()));
  }

  private IntegrationInstance instance(IntegrationPlugin plugin) {
    return instanceRepository
        .findByOwnerIdAndPluginIdAndPluginType(null, plugin.id(), plugin.type())
        .orElse(null);
  }

  private IntegrationPlugin plugin(IntegrationType type, String id) {
    return pluginRegistry
        .find(type, id)
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown integration plugin: " + type + "/" + id));
  }

  private void validateSchedule(String cron, String timezone) {
    CronExpression.parse(cron);
    ZoneId.of(timezone);
  }

  private String sanitize(String value) {
    return value == null
        ? null
        : value
            .replaceAll(
                "(?i)(api[_-]?key|access[_-]?key|token|secret)=?\\s*[^ ,;]+", "$1=[REDACTED]")
            .substring(0, Math.min(value.length(), 500));
  }

  private Map<String, String> fromJson(String json) {
    try {
      return new LinkedHashMap<>(
          objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {}));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored integration configuration", e);
    }
  }
}
