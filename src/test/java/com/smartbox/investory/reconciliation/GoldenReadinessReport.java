package com.smartbox.investory.reconciliation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Small machine-readable result contract for the deterministic golden rebuild. */
final class GoldenReadinessReport {

  private final List<Check> checks = new ArrayList<>();

  void pass(String checkId, String scope, String message) {
    checks.add(new Check(checkId, scope, null, null, null, null, "PASS", "INFO", message));
  }

  void fail(String checkId, String scope, Throwable failure) {
    String message = failure.getMessage();
    checks.add(
        new Check(
            checkId,
            scope,
            null,
            null,
            null,
            null,
            "FAIL",
            "ERROR",
            message == null || message.isBlank() ? failure.getClass().getSimpleName() : message));
  }

  boolean ready() {
    return checks.stream().noneMatch(check -> "FAIL".equals(check.status()));
  }

  String status() {
    return ready() ? "READY" : "NOT_READY";
  }

  String json() {
    try {
      return new ObjectMapper().writeValueAsString(new Report(status(), List.copyOf(checks)));
    } catch (Exception exception) {
      throw new IllegalStateException("Could not serialize golden readiness report", exception);
    }
  }

  String summary() {
    return "GOLDEN REBUILD: " + status() + " " + json();
  }

  private record Report(String status, List<Check> checks) {}

  private record Check(
      String checkId,
      String scope,
      String expected,
      String actual,
      String difference,
      String tolerance,
      String status,
      String severity,
      String message) {}
}
