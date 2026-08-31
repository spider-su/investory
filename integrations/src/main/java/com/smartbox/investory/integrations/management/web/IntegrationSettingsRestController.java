package com.smartbox.investory.integrations.management.web;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationJobCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsCommand;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.IntegrationSettingsFacade;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/integrations")
@RequiredArgsConstructor
public class IntegrationSettingsRestController {
  private final IntegrationSettingsFacade settings;

  @GetMapping
  public List<IntegrationSettingsView> list() {
    return settings.listIntegrations();
  }

  @GetMapping("/{type}/{pluginId}")
  public IntegrationSettingsView get(
      @PathVariable IntegrationType type, @PathVariable String pluginId) {
    return settings.getIntegration(type, pluginId);
  }

  @PutMapping("/{type}/{pluginId}")
  public IntegrationSettingsView save(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @RequestBody Payload payload) {
    return settings.saveConfiguration(
        new IntegrationSettingsCommand(
            type, pluginId, payload.configuration(), payload.secrets(), payload.clearSecrets()));
  }

  @PostMapping("/{type}/{pluginId}/test")
  public ConnectionTestResult test(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @RequestBody Payload payload) {
    return settings.testConnection(
        new IntegrationSettingsCommand(
            type, pluginId, payload.configuration(), payload.secrets(), payload.clearSecrets()));
  }

  @PutMapping("/{type}/{pluginId}/enabled")
  public IntegrationSettingsView enabled(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @RequestBody Enabled payload) {
    return settings.setEnabled(type, pluginId, payload.enabled());
  }

  @PutMapping("/{type}/{pluginId}/jobs/{jobType}")
  public IntegrationSettingsView.JobView job(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @PathVariable String jobType,
      @RequestBody Job payload) {
    return settings.saveJob(
        new IntegrationJobCommand(
            type,
            pluginId,
            jobType,
            payload.enabled(),
            payload.cron(),
            payload.timezone(),
            Map.of()));
  }

  public record Payload(
      Map<String, String> configuration, Map<String, String> secrets, Set<String> clearSecrets) {}

  public record Enabled(boolean enabled) {}

  public record Job(boolean enabled, String cron, String timezone) {}
}
