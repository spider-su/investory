package com.smartbox.investory.integration.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationInstance;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationInstanceRepository;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationSecret;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationSecretRepository;
import com.smartbox.investory.integration.IntegrationPlugin;
import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.PluginConfig;
import com.smartbox.investory.integration.ValidationResult;
import com.smartbox.investory.integration.registry.PluginRegistry;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationConfigurationService {
  private final IntegrationInstanceRepository instanceRepository;
  private final IntegrationSecretRepository secretRepository;
  private final PluginRegistry pluginRegistry;
  private final IntegrationSecretCipher secretCipher;
  private final ObjectMapper objectMapper;

  @Autowired
  public IntegrationConfigurationService(
      IntegrationInstanceRepository instanceRepository,
      IntegrationSecretRepository secretRepository,
      PluginRegistry pluginRegistry,
      IntegrationSecretCipher secretCipher) {
    this(instanceRepository, secretRepository, pluginRegistry, secretCipher, new ObjectMapper());
  }

  public IntegrationConfigurationService(
      IntegrationInstanceRepository instanceRepository,
      IntegrationSecretRepository secretRepository,
      PluginRegistry pluginRegistry,
      IntegrationSecretCipher secretCipher,
      ObjectMapper objectMapper) {
    this.instanceRepository = instanceRepository;
    this.secretRepository = secretRepository;
    this.pluginRegistry = pluginRegistry;
    this.secretCipher = secretCipher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public IntegrationInstance saveGlobal(
      String pluginId,
      IntegrationType type,
      Map<String, String> config,
      Map<String, String> secrets) {
    return saveGlobal(pluginId, type, config, secrets, Set.of());
  }

  @Transactional
  public IntegrationInstance saveGlobal(
      String pluginId,
      IntegrationType type,
      Map<String, String> config,
      Map<String, String> secrets,
      Set<String> clearSecrets) {
    IntegrationPlugin plugin =
        pluginRegistry
            .find(type, pluginId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unknown integration plugin: " + type + "/" + pluginId));
    IntegrationInstance instance =
        instanceRepository
            .findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type)
            .orElseGet(IntegrationInstance::new);
    Map<String, String> persistedConfig =
        instance.getId() == null ? new LinkedHashMap<>() : fromJson(instance.getConfigJson());
    Map<String, String> effective = new LinkedHashMap<>(persistedConfig);
    if (config != null) effective.putAll(config);
    Map<String, String> persistedSecrets = new LinkedHashMap<>();
    if (instance.getId() != null) {
      secretRepository
          .findByIntegrationInstanceId(instance.getId())
          .forEach(
              secret ->
                  persistedSecrets.put(
                      secret.getSecretName(), secretCipher.decrypt(secret.getCiphertext())));
    }
    if (secrets != null) effective.putAll(secrets);
    Set<String> requestedClear = clearSecrets == null ? Set.of() : clearSecrets;
    requestedClear.forEach(effective::remove);
    persistedSecrets
        .keySet()
        .forEach(
            key -> {
              if (!requestedClear.contains(key) && (secrets == null || !secrets.containsKey(key))) {
                effective.put(key, persistedSecrets.get(key));
              }
            });
    ValidationResult validation = plugin.validate(new PluginConfig(effective));
    if (!validation.valid()) {
      throw new IllegalArgumentException(
          "Invalid integration configuration: " + String.join("; ", validation.errors()));
    }
    if (instance.getCreatedAt() == null) instance.setCreatedAt(ZonedDateTime.now());
    instance.setOwnerId(null);
    instance.setPluginId(pluginId);
    instance.setPluginType(type);
    Map<String, String> storedConfig = new LinkedHashMap<>(effective);
    if (secrets != null) secrets.keySet().forEach(storedConfig::remove);
    persistedSecrets.keySet().forEach(storedConfig::remove);
    instance.setConfigJson(toJson(storedConfig));
    instance.setUpdatedAt(ZonedDateTime.now());
    IntegrationInstance saved = instanceRepository.save(instance);
    if (secrets != null) {
      for (Map.Entry<String, String> entry : secrets.entrySet()) {
        IntegrationSecret secret =
            secretRepository
                .findByIntegrationInstanceIdAndSecretName(saved.getId(), entry.getKey())
                .orElseGet(IntegrationSecret::new);
        secret.setIntegrationInstanceId(saved.getId());
        secret.setSecretName(entry.getKey());
        secret.setCiphertext(secretCipher.encrypt(entry.getValue()));
        secret.setKeyVersion(secretCipher.keyVersion());
        secret.setUpdatedAt(ZonedDateTime.now());
        secretRepository.save(secret);
      }
    }
    requestedClear.forEach(
        name -> secretRepository.deleteByIntegrationInstanceIdAndSecretName(saved.getId(), name));
    return saved;
  }

  @Transactional(readOnly = true)
  public PluginConfig resolve(IntegrationInstance instance) {
    Map<String, String> values = fromJson(instance.getConfigJson());
    secretRepository
        .findByIntegrationInstanceId(instance.getId())
        .forEach(
            secret ->
                values.put(secret.getSecretName(), secretCipher.decrypt(secret.getCiphertext())));
    return new PluginConfig(values);
  }

  @Transactional(readOnly = true)
  public Optional<PluginConfig> resolveEnabledGlobal(IntegrationType type, String pluginId) {
    return instanceRepository
        .findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type)
        .filter(IntegrationInstance::isEnabled)
        .map(this::resolve);
  }

  /** Persisted enabled settings win; an empty fallback keeps environment-only installs working. */
  @Transactional(readOnly = true)
  public PluginConfig resolveForRuntime(
      IntegrationType type, String pluginId, PluginConfig environmentFallback) {
    return resolveEnabledGlobal(type, pluginId)
        .orElse(environmentFallback == null ? PluginConfig.empty() : environmentFallback);
  }

  private String toJson(Map<String, String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Cannot serialize integration configuration", exception);
    }
  }

  private Map<String, String> fromJson(String json) {
    try {
      return new LinkedHashMap<>(
          objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {}));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored integration configuration", exception);
    }
  }
}
