package com.smartbox.investory.integrations.management.api.model;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record IntegrationSettingsView(
    String pluginId,
    String name,
    IntegrationType type,
    String description,
    List<PluginFieldDescriptor> fields,
    Map<String, String> values,
    Map<String, SecretState> secrets,
    boolean enabled,
    boolean testSupported,
    ZonedDateTime lastTestAt,
    String lastTestStatus,
    String lastTestMessage,
    List<IntegrationJobDescriptor> availableJobs,
    List<JobView> jobs) {
  public IntegrationSettingsView {
    fields = fields == null ? List.of() : List.copyOf(fields);
    values = values == null ? Map.of() : Map.copyOf(values);
    secrets = secrets == null ? Map.of() : Map.copyOf(secrets);
    availableJobs = availableJobs == null ? List.of() : List.copyOf(availableJobs);
    jobs = jobs == null ? List.of() : List.copyOf(jobs);
  }

  public record SecretState(boolean configured) {}

  public String purpose() {
    return switch (type) {
      case MARKET_DATA -> "Market data";
      case FX_DATA -> "FX";
      case NOTIFICATION -> "Notifications";
      case AI -> "AI";
      case EXPORT -> "Export";
      case BROKER_IMPORT -> "Imports";
    };
  }

  public String typeLabel() {
    return purpose();
  }

  public boolean configured() {
    return fields.stream()
        .filter(PluginFieldDescriptor::required)
        .allMatch(
            field ->
                field.type().name().equals("SECRET")
                    ? secrets.getOrDefault(field.key(), new SecretState(false)).configured()
                    : values.containsKey(field.key()) && !values.get(field.key()).isBlank());
  }

  public String status() {
    if (enabled && jobs.stream().anyMatch(job -> "FAILED".equals(job.status())))
      return "JOB_FAILED";
    if ("FAILED".equals(lastTestStatus)) return "TEST_FAILED";
    if (!configured()) return "NOT_CONFIGURED";
    if (!enabled) return "DISABLED";
    return "SUCCESS".equals(lastTestStatus) ? "CONNECTED" : "ENABLED_UNTESTED";
  }

  public boolean needsAttention() {
    return !configured() || "TEST_FAILED".equals(status()) || "JOB_FAILED".equals(status());
  }

  public String statusLabel() {
    return switch (status()) {
      case "CONNECTED" -> "Connected";
      case "ENABLED_UNTESTED" -> "Enabled — untested";
      case "DISABLED" -> "Disabled";
      case "TEST_FAILED" -> "Test failed";
      case "JOB_FAILED" -> "Job failed";
      default -> "Needs configuration";
    };
  }

  public String jobCron(String jobType) {
    return jobs.stream()
        .filter(job -> job.jobType().equals(jobType))
        .findFirst()
        .map(JobView::cron)
        .orElseGet(
            () ->
                availableJobs.stream()
                    .filter(job -> job.jobType().equals(jobType))
                    .findFirst()
                    .map(IntegrationJobDescriptor::defaultCron)
                    .orElse("0 0 * * * *"));
  }

  public String jobTimezone(String jobType) {
    return jobs.stream()
        .filter(job -> job.jobType().equals(jobType))
        .findFirst()
        .map(JobView::timezone)
        .orElse("Europe/Warsaw");
  }

  public boolean jobEnabled(String jobType) {
    return jobs.stream()
        .filter(job -> job.jobType().equals(jobType))
        .findFirst()
        .map(JobView::enabled)
        .orElse(false);
  }

  public record JobView(
      String jobType,
      boolean enabled,
      String cron,
      String timezone,
      ZonedDateTime lastExecution,
      String status,
      String lastError) {}
}
