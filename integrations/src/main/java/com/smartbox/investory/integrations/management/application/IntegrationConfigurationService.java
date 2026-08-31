package com.smartbox.investory.integrations.management.application;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.model.PluginConfig;
import com.smartbox.investory.integrations.management.model.ValidationResult;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationSecretEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationSecretRepository;
import com.smartbox.investory.integrations.management.spi.IntegrationPlugin;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

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
      IntegrationSecretCipher secretCipher,
      ObjectMapper objectMapper) {
    this.instanceRepository = instanceRepository;
    this.secretRepository = secretRepository;
    this.pluginRegistry = pluginRegistry;
    this.secretCipher = secretCipher;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public IntegrationInstanceEntity saveGlobal(
      String pluginId,
      IntegrationType type,
      Map<String, String> config,
      Map<String, String> secrets) {
    return saveGlobal(pluginId, type, config, secrets, Set.of());
  }

  @Transactional
  public IntegrationInstanceEntity saveGlobal(
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
    IntegrationInstanceEntity instance =
        instanceRepository
            .findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type)
            .orElseGet(IntegrationInstanceEntity::new);
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
    // Provider-level enablement was retired; the instance flag is authoritative.
    effective.remove("enabled");
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
    // Any configuration change invalidates the previous connectivity result.
    instance.setLastTestAt(null);
    instance.setLastTestStatus(null);
    instance.setLastTestMessage(null);
    instance.setUpdatedAt(ZonedDateTime.now());
    IntegrationInstanceEntity saved = instanceRepository.save(instance);
    if (secrets != null) {
      for (Map.Entry<String, String> entry : secrets.entrySet()) {
        IntegrationSecretEntity secret =
            secretRepository
                .findByIntegrationInstanceIdAndSecretName(saved.getId(), entry.getKey())
                .orElseGet(IntegrationSecretEntity::new);
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
  public PluginConfig resolve(IntegrationInstanceEntity instance) {
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
        .filter(IntegrationInstanceEntity::isEnabled)
        .map(this::resolve);
  }

  /** A persisted instance is authoritative, including an explicit disabled state. */
  @Transactional(readOnly = true)
  public PluginConfig resolveForRuntime(
      IntegrationType type, String pluginId, PluginConfig environmentFallback) {
    Optional<IntegrationInstanceEntity> persisted =
        instanceRepository.findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type);
    if (persisted.isPresent()) {
      return persisted.get().isEnabled() ? resolve(persisted.get()) : PluginConfig.empty();
    }
    return environmentFallback == null ? PluginConfig.empty() : environmentFallback;
  }

  private String toJson(Map<String, String> values) {
    try {
      return objectMapper.writeValueAsString(values);
    } catch (JacksonException exception) {
      throw new IllegalArgumentException("Cannot serialize integration configuration", exception);
    }
  }

  private Map<String, String> fromJson(String json) {
    try {
      return new LinkedHashMap<>(
          objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {}));
    } catch (JacksonException exception) {
      throw new IllegalStateException("Invalid stored integration configuration", exception);
    }
  }
}
