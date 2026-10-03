package com.smartbox.investory.integrations.management.scheduling;

import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import com.smartbox.investory.shared.notifications.NotificationCandidate;
import com.smartbox.investory.shared.notifications.NotificationEventPublisher;
import com.smartbox.investory.shared.notifications.NotificationEventType;
import com.smartbox.investory.shared.notifications.NotificationSeverity;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationJobAlertPublisher {
  private final NotificationEventPublisher events;
  private final ApplicationTime applicationTime;

  public void failed(
      IntegrationJobEntity job,
      IntegrationInstanceEntity instance,
      ZonedDateTime startedAt,
      String error) {
    publish(job, instance, "FAILED", startedAt.toInstant(), null, error);
  }

  public void stale(
      IntegrationJobEntity job, IntegrationInstanceEntity instance, ZonedDateTime expectedAt) {
    publish(job, instance, "STALE", expectedAt.toInstant(), expectedAt, null);
  }

  private void publish(
      IntegrationJobEntity job,
      IntegrationInstanceEntity instance,
      String status,
      Instant incidentAt,
      ZonedDateTime expectedAt,
      String error) {
    try {
      String operation = job.getJobType();
      String fingerprint = "INTEGRATION_JOB_ALERT:" + job.getId() + ":" + status + ":" + incidentAt;
      Map<String, String> payload = new LinkedHashMap<>();
      payload.put("integrationType", instance.getPluginType().name());
      payload.put("integration", instance.getPluginId());
      payload.put("jobType", operation);
      payload.put("status", status);
      if (expectedAt != null) payload.put("expectedAt", expectedAt.toString());
      if (error != null && !error.isBlank()) payload.put("error", error);
      events.publish(
          new NotificationCandidate(
              NotificationEventType.INTEGRATION_JOB_ALERT,
              "STALE".equals(status) ? NotificationSeverity.WARNING : NotificationSeverity.ERROR,
              null,
              "INTEGRATION_JOB",
              job.getId().toString(),
              fingerprint,
              "Integration job " + status.toLowerCase() + ": " + operation,
              payload,
              applicationTime.now()));
    } catch (RuntimeException exception) {
      log.error("Could not persist integration job alert jobId={}", job.getId(), exception);
    }
  }
}
