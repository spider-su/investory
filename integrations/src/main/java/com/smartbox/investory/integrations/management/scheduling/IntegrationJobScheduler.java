package com.smartbox.investory.integrations.management.scheduling;

import com.smartbox.investory.integrations.management.api.IntegrationJobExecutionApi;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobRepository;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** Polls persisted jobs so changes take effect without an application restart. */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class IntegrationJobScheduler implements IntegrationJobExecutionApi {
  private final IntegrationJobRepository jobRepository;
  private final IntegrationInstanceRepository instanceRepository;
  private final IntegrationJobHandlerRegistry handlerRegistry;
  private final ApplicationTime applicationTime;
  private final JdbcTemplate jdbcTemplate;

  @Scheduled(fixedDelayString = "${app.integrations.scheduler-poll-ms:60000}")
  public void poll() {
    ZonedDateTime now = applicationTime.now(applicationTime.businessZone());
    for (IntegrationJobEntity job : jobRepository.findByEnabledTrue()) {
      IntegrationInstanceEntity instance =
          instanceRepository.findById(job.getIntegrationInstanceId()).orElse(null);
      if (instance == null || !instance.isEnabled() || !isDue(job, now)) {
        continue;
      }
      long lockKey = lockKey(instance, job);
      jdbcTemplate.execute(
          (ConnectionCallback<Void>)
              connection -> {
                if (!advisoryLock(connection, "select pg_try_advisory_lock(?)", lockKey)) {
                  log.info("Skipping overlapping integration job {}", job.getId());
                  return null;
                }
                try {
                  run(job, instance, now);
                } finally {
                  advisoryLock(connection, "select pg_advisory_unlock(?)", lockKey);
                }
                return null;
              });
    }
  }

  @Override
  public void runNow(IntegrationType type, String pluginId, String jobType) {
    IntegrationInstanceEntity instance =
        instanceRepository
            .findByOwnerIdAndPluginIdAndPluginType(null, pluginId, type)
            .orElseThrow(
                () -> new IllegalArgumentException("Save integration configuration first"));
    IntegrationJobEntity job = new IntegrationJobEntity();
    job.setJobType(jobType);
    job.setTimezone("Europe/Warsaw");
    handlerRegistry
        .require(type, jobType)
        .execute(
            new IntegrationJobContext(
                instance, job, applicationTime.now(applicationTime.businessZone())));
  }

  private boolean advisoryLock(Connection connection, String sql, long lockKey)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, lockKey);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      }
    }
  }

  private long lockKey(IntegrationInstanceEntity instance, IntegrationJobEntity job) {
    return ((long) instance.getId().hashCode() << 32) ^ job.getJobType().hashCode();
  }

  private boolean isDue(IntegrationJobEntity job, ZonedDateTime now) {
    try {
      ZoneId zone = ZoneId.of(job.getTimezone());
      ZonedDateTime reference = job.getLastCompletedAt();
      if (reference == null) {
        return true;
      }
      ZonedDateTime next =
          CronExpression.parse(job.getCron()).next(reference.withZoneSameInstant(zone));
      return next != null && !next.isAfter(now.withZoneSameInstant(zone));
    } catch (RuntimeException exception) {
      log.warn("Skipping invalid integration job {}: {}", job.getId(), exception.getMessage());
      return false;
    }
  }

  private void run(
      IntegrationJobEntity job, IntegrationInstanceEntity instance, ZonedDateTime now) {
    job.setLastStartedAt(now);
    job.setLastStatus("STARTED");
    job.setLastError(null);
    jobRepository.save(job);
    try {
      handlerRegistry
          .require(instance.getPluginType(), job.getJobType())
          .execute(new IntegrationJobContext(instance, job, now));
      job.setLastStatus("SUCCESS");
    } catch (Exception exception) {
      job.setLastStatus("FAILED");
      job.setLastError(sanitizeError(exception.getMessage()));
      log.warn("Integration job {} failed: {}", job.getId(), exception.getMessage());
    } finally {
      job.setLastCompletedAt(applicationTime.now(applicationTime.businessZone()));
      jobRepository.save(job);
    }
  }

  private String sanitizeError(String message) {
    if (message == null) return null;
    String sanitized =
        message.replaceAll(
            "(?i)(api[_-]?key|access[_-]?key|token|secret)=?\\s*[^ ,;]+", "$1=[REDACTED]");
    return sanitized.substring(0, Math.min(500, sanitized.length()));
  }
}
