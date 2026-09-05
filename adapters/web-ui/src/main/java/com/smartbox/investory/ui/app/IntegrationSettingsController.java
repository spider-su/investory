package com.smartbox.investory.ui.app;

import com.smartbox.investory.integrations.management.api.model.ConnectionTestResult;
import com.smartbox.investory.integrations.management.api.model.IntegrationSettingsView;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class IntegrationSettingsController {
  private final IntegrationSettingsClient settings;

  /** Compatibility constructor for UI tests that cover provider settings only. */
  public IntegrationSettingsController(IntegrationSettingsClient settings) {
    this.settings = settings;
  }

  @GetMapping("/settings/integrations")
  public String page(Model model, @RequestParam(required = false) Long portfolioId) {
    var integrations = settings.list();
    model.addAttribute("portfolioId", portfolioId);
    model.addAttribute("integrations", integrations);
    model.addAttribute(
        "activeCount", integrations.stream().filter(IntegrationSettingsView::enabled).count());
    model.addAttribute(
        "attentionCount",
        integrations.stream().filter(IntegrationSettingsView::needsAttention).count());
    model.addAttribute(
        "setupCount", integrations.stream().filter(view -> !view.configured()).count());
    model.addAttribute(
        "failedCount",
        integrations.stream()
            .filter(
                view -> "TEST_FAILED".equals(view.status()) || "JOB_FAILED".equals(view.status()))
            .count());
    return "integration-settings";
  }

  @PostMapping("/settings/integrations/{type}/{pluginId}")
  public String save(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @RequestParam Map<String, String> params,
      RedirectAttributes redirect) {
    Map<String, String> configuration = new LinkedHashMap<>();
    Map<String, String> secrets = new LinkedHashMap<>();
    java.util.Set<String> clearSecrets = new java.util.HashSet<>();
    params.forEach(
        (key, value) -> {
          if (key.startsWith("secret.")) secrets.put(key.substring(7), value);
          else if (key.startsWith("clearSecret.") && "true".equals(value))
            clearSecrets.add(key.substring(12));
          else if (!key.equals("enabled") && !key.equals("action")) configuration.put(key, value);
        });
    try {
      if ("test".equals(params.get("action"))) {
        ConnectionTestResult result =
            settings.test(type, pluginId, configuration, secrets, clearSecrets);
        redirect.addFlashAttribute(
            result.success() ? "success" : "error", "Tested but not saved. " + result.message());
      } else {
        settings.save(type, pluginId, configuration, secrets, clearSecrets);
        redirect.addFlashAttribute("success", "Settings saved");
      }
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings/integrations";
  }

  @PostMapping("/settings/integrations/{type}/{pluginId}/jobs/{jobType}")
  public String job(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @PathVariable String jobType,
      @RequestParam(defaultValue = "false") boolean enabled,
      @RequestParam String cron,
      @RequestParam String timezone,
      @RequestParam(defaultValue = "save") String action,
      RedirectAttributes redirect) {
    try {
      if ("test".equals(action)) {
        settings.runJobNow(type, pluginId, jobType);
        redirect.addFlashAttribute("success", "Test notification sent");
      } else {
        settings.saveJob(type, pluginId, jobType, enabled, cron, timezone);
        redirect.addFlashAttribute("success", "Schedule saved");
      }
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings/integrations";
  }

  String job(
      IntegrationType type,
      String pluginId,
      String jobType,
      boolean enabled,
      String cron,
      String timezone,
      RedirectAttributes redirect) {
    return job(type, pluginId, jobType, enabled, cron, timezone, "save", redirect);
  }

  @PostMapping("/settings/integrations/{type}/{pluginId}/enabled")
  public String enabled(
      @PathVariable IntegrationType type,
      @PathVariable String pluginId,
      @RequestParam boolean enabled,
      RedirectAttributes redirect) {
    try {
      settings.setEnabled(type, pluginId, enabled);
      redirect.addFlashAttribute(
          "success", enabled ? "Integration enabled" : "Integration disabled");
    } catch (RuntimeException e) {
      redirect.addFlashAttribute("error", e.getMessage());
    }
    return "redirect:/settings/integrations";
  }
}
