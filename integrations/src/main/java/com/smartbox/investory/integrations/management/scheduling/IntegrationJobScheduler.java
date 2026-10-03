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
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** Polls persisted jobs so changes take effect without an application restart. */
@Slf4j
@Service
public class IntegrationJobScheduler implements IntegrationJobExecutionApi {
  private final IntegrationJobRepository jobRepository;
  private final IntegrationInstanceRepository instanceRepository;
  private final IntegrationJobHandlerRegistry handlerRegistry;
  private final ApplicationTime applicationTime;
  private final JdbcTemplate jdbcTemplate;
  private final IntegrationJobAlertPublisher alerts;
  private final Duration staleGrace;

  public IntegrationJobScheduler(
      IntegrationJobRepository jobRepository,
      IntegrationInstanceRepository instanceRepository,
      IntegrationJobHandlerRegistry handlerRegistry,
      ApplicationTime applicationTime,
      JdbcTemplate jdbcTemplate,
      IntegrationJobAlertPublisher alerts,
      @Value("${app.integrations.scheduler-stale-grace-minutes:360}") long staleGraceMinutes) {
    this.jobRepository = jobRepository;
    this.instanceRepository = instanceRepository;
    this.handlerRegistry = handlerRegistry;
    this.applicationTime = applicationTime;
    this.jdbcTemplate = jdbcTemplate;
    this.alerts = alerts;
    this.staleGrace = Duration.ofMinutes(Math.max(1, staleGraceMinutes));
  }

  @Scheduled(fixedDelayString = "${app.integrations.scheduler-poll-ms:60000}")
  public void poll() {
    ZonedDateTime now = applicationTime.now(applicationTime.businessZone());
    for (IntegrationJobEntity job : jobRepository.findByEnabledTrue()) {
      IntegrationInstanceEntity instance =
          instanceRepository.findById(job.getIntegrationInstanceId()).orElse(null);
      if (instance == null || !instance.isEnabled()) {
        continue;
      }
      checkStale(job, instance, now);
      if (!isDue(job, now)) continue;
      long lockKey = lockKey(instance, job);
      jdbcTemplate.execute(
          (ConnectionCallback<Void>)
              connection -> {
                if (!advisoryLock(connection, "select pg_try_advisory_lock(?)", lockKey)) {
                  log.info("Skipping overlapping integration job {}", job.getId());
                  return null;
                }
                try {
                  run(job, instance, now, false);
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
    IntegrationJobEntity job =
        jobRepository.findByIntegrationInstanceId(instance.getId()).stream()
            .filter(candidate -> candidate.getJobType().equals(jobType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Save the integration job first"));
    executeLocked(
        instance,
        job,
        () -> run(job, instance, applicationTime.now(applicationTime.businessZone()), true));
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

  private void checkStale(
      IntegrationJobEntity job, IntegrationInstanceEntity instance, ZonedDateTime now) {
    if (!"refresh-rates".equals(job.getJobType()) && !"refresh-prices".equals(job.getJobType()))
      return;
    ZonedDateTime completed = job.getLastCompletedAt();
    if (completed == null) return;
    try {
      ZoneId zone = ZoneId.of(job.getTimezone());
      ZonedDateTime expected =
          org.springframework.scheduling.support.CronExpression.parse(job.getCron())
              .next(completed.withZoneSameInstant(zone));
      ZonedDateTime staleAt = expected == null ? null : expected.plus(staleGrace);
      boolean recentlyStarted =
          staleAt != null
              && "STARTED".equals(job.getLastStatus())
              && job.getLastStartedAt() != null
              && !job.getLastStartedAt().isBefore(staleAt);
      if (staleAt != null && now.withZoneSameInstant(zone).isAfter(staleAt) && !recentlyStarted) {
        alerts.stale(job, instance, expected);
      }
    } catch (RuntimeException exception) {
      log.warn(
          "Skipping stale check for invalid integration job {}: {}",
          job.getId(),
          exception.getMessage());
    }
  }

  private void run(
      IntegrationJobEntity job,
      IntegrationInstanceEntity instance,
      ZonedDateTime now,
      boolean rethrowFailure) {
    job.setLastStartedAt(now);
    job.setLastStatus("STARTED");
    job.setLastError(null);
    jobRepository.save(job);
    Exception failure = null;
    try {
      handlerRegistry
          .require(instance.getPluginType(), job.getJobType())
          .execute(new IntegrationJobContext(instance, job, now));
      job.setLastStatus("SUCCESS");
    } catch (Exception exception) {
      failure = exception;
      job.setLastStatus("FAILED");
      String detail = exception.getMessage();
      job.setLastError(
          sanitizeError(
              detail == null || detail.isBlank() ? exception.getClass().getSimpleName() : detail));
      log.warn("Integration job {} failed: {}", job.getId(), job.getLastError());
      alerts.failed(job, instance, now, job.getLastError());
    } finally {
      job.setLastCompletedAt(applicationTime.now(applicationTime.businessZone()));
      jobRepository.save(job);
    }
    if (failure != null && rethrowFailure)
      throw new IllegalStateException("Integration job failed: " + job.getLastError(), failure);
  }

  private void executeLocked(
      IntegrationInstanceEntity instance, IntegrationJobEntity job, Runnable execution) {
    long key = lockKey(instance, job);
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              if (!advisoryLock(connection, "select pg_try_advisory_lock(?)", key))
                throw new IllegalStateException("Integration job is already running");
              try {
                execution.run();
              } finally {
                advisoryLock(connection, "select pg_advisory_unlock(?)", key);
              }
              return null;
            });
  }

  private String sanitizeError(String message) {
    if (message == null) return null;
    String sanitized =
        message.replaceAll(
            "(?i)(api[_-]?key|access[_-]?key|token|secret)=?\\s*[^ ,;]+", "$1=[REDACTED]");
    return sanitized.substring(0, Math.min(500, sanitized.length()));
  }
}
