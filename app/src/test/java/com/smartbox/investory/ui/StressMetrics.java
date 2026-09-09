package com.smartbox.investory.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

final class StressMetrics {
  private final LongAdder started = new LongAdder();
  private final LongAdder passed = new LongAdder();
  private final LongAdder failed = new LongAdder();
  private final LongAdder requests = new LongAdder();
  private final LongAdder failedRequests = new LongAdder();
  private final LongAdder http429 = new LongAdder();
  private final LongAdder http5xx = new LongAdder();
  private final LongAdder writes = new LongAdder();
  private final LongAdder consoleErrors = new LongAdder();
  private final LongAdder pageErrors = new LongAdder();
  private final LongAdder criticalFailures = new LongAdder();
  private final List<Long> initial = Collections.synchronizedList(new ArrayList<>());
  private final List<Long> reload = Collections.synchronizedList(new ArrayList<>());
  private final List<String> writeDetails = Collections.synchronizedList(new ArrayList<>());
  private final List<String> failureDetails = Collections.synchronizedList(new ArrayList<>());
  private volatile boolean repeated429;

  StressMetrics(int ignoredExpectedScenarios) {}

  void scenarioStarted() {
    started.increment();
  }

  void scenarioPassed() {
    passed.increment();
  }

  void scenarioFailure(Throwable failure) {
    failed.increment();
    failureDetails.add(failure.toString());
  }

  void requestStarted() {
    requests.increment();
  }

  void failedRequest() {
    failedRequests.increment();
  }

  void initialDuration(long nanos) {
    initial.add(nanos / 1_000_000);
  }

  void reloadDuration(long nanos) {
    reload.add(nanos / 1_000_000);
  }

  void unexpectedWrite(String detail) {
    writes.increment();
    writeDetails.add(detail);
  }

  void consoleError(String ignored) {
    consoleErrors.increment();
  }

  void pageError(String ignored) {
    pageErrors.increment();
  }

  void criticalFailure(String ignored) {
    criticalFailures.increment();
  }

  void response(int status) {
    if (status >= 400) failedRequests.increment();
    if (status == 429) {
      http429.increment();
      repeated429 = http429.sum() >= 2;
    }
    if (status >= 500) http5xx.increment();
  }

  long unexpectedWrites() {
    return writes.sum();
  }

  long http5xx() {
    return http5xx.sum();
  }

  long criticalFailures() {
    return criticalFailures.sum();
  }

  long pageErrors() {
    return pageErrors.sum();
  }

  long consoleErrors() {
    return consoleErrors.sum();
  }

  long failedScenarios() {
    return failed.sum();
  }

  boolean repeated429() {
    return repeated429;
  }

  String summary(LongTermAssetsReadOnlyStress.Config config) {
    return String.format(
        "Long-Term Assets stress: users=%d iterations=%d started=%d passed=%d failed=%d requests=%d failedRequests=%d 429=%d 5xx=%d writes=%d consoleErrors=%d pageErrors=%d initial(p50/p95/max)=%s reload(p50/p95/max)=%s",
        config.users(),
        config.iterations(),
        started.sum(),
        passed.sum(),
        failed.sum(),
        requests.sum(),
        failedRequests.sum(),
        http429.sum(),
        http5xx.sum(),
        writes.sum(),
        consoleErrors.sum(),
        pageErrors.sum(),
        stats(initial),
        stats(reload));
  }

  String json(LongTermAssetsReadOnlyStress.Config config) {
    return "{\"baseUrl\":\""
        + config.baseUrl()
        + "\",\"users\":"
        + config.users()
        + ",\"iterations\":"
        + config.iterations()
        + ",\"scenariosStarted\":"
        + started.sum()
        + ",\"scenariosPassed\":"
        + passed.sum()
        + ",\"scenariosFailed\":"
        + failed.sum()
        + ",\"totalHttpRequests\":"
        + requests.sum()
        + ",\"failedHttpRequests\":"
        + failedRequests.sum()
        + ",\"http429\":"
        + http429.sum()
        + ",\"http5xx\":"
        + http5xx.sum()
        + ",\"unexpectedWriteAttempts\":"
        + writes.sum()
        + ",\"javascriptConsoleErrors\":"
        + consoleErrors.sum()
        + ",\"pageErrors\":"
        + pageErrors.sum()
        + ",\"initialLoad\":"
        + statsJson(initial)
        + ",\"reload\":"
        + statsJson(reload)
        + ",\"writeDetails\":"
        + jsonArray(writeDetails)
        + ",\"failureDetails\":"
        + jsonArray(failureDetails)
        + "}";
  }

  private static String stats(List<Long> values) {
    return values.isEmpty() ? "n/a" : statsJson(values);
  }

  private static String statsJson(List<Long> values) {
    if (values.isEmpty()) return "{\"p50Ms\":null,\"p95Ms\":null,\"maxMs\":null}";
    List<Long> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    return "{\"p50Ms\":"
        + percentile(sorted, .50)
        + ",\"p95Ms\":"
        + percentile(sorted, .95)
        + ",\"maxMs\":"
        + sorted.get(sorted.size() - 1)
        + "}";
  }

  private static long percentile(List<Long> sorted, double p) {
    return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p * sorted.size()) - 1));
  }

  private static String jsonArray(List<String> values) {
    if (values.isEmpty()) return "[]";
    return "[\"" + String.join("\",\"", values) + "\"]";
  }
}
