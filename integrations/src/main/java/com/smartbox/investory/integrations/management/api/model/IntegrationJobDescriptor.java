package com.smartbox.investory.integrations.management.api.model;

public record IntegrationJobDescriptor(
    String jobType, String label, String defaultCron, String defaultTimezone) {
  public static IntegrationJobDescriptor forType(String jobType) {
    return switch (jobType) {
      case "refresh-prices" ->
          new IntegrationJobDescriptor(jobType, "Refresh prices", "0 0 */6 * * *", "Europe/Warsaw");
      case "refresh-rates" ->
          new IntegrationJobDescriptor(
              jobType, "Refresh FX rates", "0 15 6 * * *", "Europe/Warsaw");
      case "audit-long-term-payments" ->
          new IntegrationJobDescriptor(
              jobType, "Check long-term payments", "0 0 11 5 * *", "Europe/Warsaw");
      default -> new IntegrationJobDescriptor(jobType, jobType, "0 0 * * * *", "Europe/Warsaw");
    };
  }
}
