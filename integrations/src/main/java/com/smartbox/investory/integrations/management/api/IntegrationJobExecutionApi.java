package com.smartbox.investory.integrations.management.api;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;

/** Public in-process boundary for immediate integration job execution. */
public interface IntegrationJobExecutionApi {
  void runNow(IntegrationType type, String pluginId, String jobType);
}
