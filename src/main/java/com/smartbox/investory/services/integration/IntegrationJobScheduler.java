package com.smartbox.investory.services.integration;

import com.smartbox.investory.infrastructure.repository.integration.IntegrationInstance;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationInstanceRepository;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationJob;
import com.smartbox.investory.infrastructure.repository.integration.IntegrationJobRepository;
import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.PluginConfig;
import com.smartbox.investory.integration.config.IntegrationConfigurationService;
import com.smartbox.investory.integration.fx.ExchangeRateHostFxDataPlugin;
import com.smartbox.investory.services.MarketService;
import com.smartbox.investory.services.currency.CurrencyRateUpdaterService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Polls persisted jobs so changes take effect without an application restart. */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationJobScheduler {
  private final IntegrationJobRepository jobRepository;
  private final IntegrationInstanceRepository instanceRepository;
  private final CurrencyRateUpdaterService currencyRateUpdaterService;
  private final MarketService marketService;
  private final JdbcTemplate jdbcTemplate;
  private final IntegrationConfigurationService configurationService;

  @Scheduled(fixedDelayString = "${app.integrations.scheduler-poll-ms:60000}")
  @Transactional
  public void poll() {
    ZonedDateTime now = ZonedDateTime.now();
    for (IntegrationJob job : jobRepository.findByEnabledTrue()) {
      IntegrationInstance instance =
          instanceRepository.findById(job.getIntegrationInstanceId()).orElse(null);
      if (instance == null || !instance.isEnabled() || !isDue(job, now)) {
        continue;
      }
      long lockKey = lockKey(instance, job);
      if (!tryLock(lockKey)) {
        log.info("Skipping overlapping integration job {}", job.getId());
        continue;
      }
      try {
        run(job, instance, now);
      } finally {
        jdbcTemplate.queryForObject("select pg_advisory_unlock(?)", Boolean.class, lockKey);
      }
    }
  }

  private boolean tryLock(long lockKey) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, lockKey));
  }

  private long lockKey(IntegrationInstance instance, IntegrationJob job) {
    return ((long) instance.getId().hashCode() << 32) ^ job.getJobType().hashCode();
  }

  private boolean isDue(IntegrationJob job, ZonedDateTime now) {
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

  private void run(IntegrationJob job, IntegrationInstance instance, ZonedDateTime now) {
    job.setLastStartedAt(now);
    job.setLastStatus("STARTED");
    job.setLastError(null);
    jobRepository.save(job);
    try {
      if (instance.getPluginType() == IntegrationType.FX_DATA
          && "refresh-rates".equals(job.getJobType())) {
        PluginConfig config =
            configurationService
                .resolveEnabledGlobal(IntegrationType.FX_DATA, ExchangeRateHostFxDataPlugin.ID)
                .orElse(PluginConfig.empty());
        currencyRateUpdaterService.updateCurrencyRatesForDate(java.time.LocalDate.now(), config);
      } else if (instance.getPluginType() == IntegrationType.MARKET_DATA
          && "refresh-prices".equals(job.getJobType())) {
        marketService.updateStocks();
      } else {
        throw new IllegalArgumentException(
            "Unsupported integration job: " + instance.getPluginType() + "/" + job.getJobType());
      }
      job.setLastStatus("SUCCESS");
    } catch (Exception exception) {
      job.setLastStatus("FAILED");
      job.setLastError(sanitizeError(exception.getMessage()));
      log.warn("Integration job {} failed: {}", job.getId(), exception.getMessage());
    } finally {
      job.setLastCompletedAt(ZonedDateTime.now());
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
