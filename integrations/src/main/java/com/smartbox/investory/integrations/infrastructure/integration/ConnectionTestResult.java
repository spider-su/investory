package com.smartbox.investory.integrations.infrastructure.integration;

public record ConnectionTestResult(boolean supported, boolean success, String message) {
  public static ConnectionTestResult unsupported() {
    return new ConnectionTestResult(false, false, "Connection test is not supported");
  }
}
