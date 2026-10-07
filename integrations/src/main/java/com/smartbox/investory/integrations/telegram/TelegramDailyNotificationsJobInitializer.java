package com.smartbox.investory.integrations.telegram;

import com.smartbox.investory.integrations.management.api.model.IntegrationJobDescriptor;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobRepository;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobHandlerRegistry;
import com.smartbox.investory.shared.time.ApplicationTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the Telegram daily-notifications job when an enabled Telegram instance lacks one. */
@Component
@RequiredArgsConstructor
public class TelegramDailyNotificationsJobInitializer {
  private final IntegrationInstanceRepository instanceRepository;
  private final IntegrationJobRepository jobRepository;
  private final IntegrationJobHandlerRegistry handlerRegistry;
  private final ApplicationTime applicationTime;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void ensureDailyNotificationsJob() {
    Optional<IntegrationInstanceEntity> instance =
        instanceRepository.findByOwnerIdAndPluginIdAndPluginType(
            null, TelegramIntegrationPlugin.ID, IntegrationType.NOTIFICATION);
    if (instance.isEmpty() || !instance.get().isEnabled()) return;

    IntegrationInstanceEntity telegram = instance.get();
    String jobType = TelegramIntegrationPlugin.DAILY_NOTIFICATIONS_JOB;
    if (!handlerRegistry.supports(IntegrationType.NOTIFICATION, jobType)) return;
    boolean exists =
        jobRepository.findByIntegrationInstanceId(telegram.getId()).stream()
            .anyMatch(job -> jobType.equals(job.getJobType()));
    if (exists) return;

    IntegrationJobDescriptor descriptor = IntegrationJobDescriptor.forType(jobType);
    IntegrationJobEntity job = new IntegrationJobEntity();
    job.setIntegrationInstanceId(telegram.getId());
    job.setJobType(jobType);
    job.setEnabled(true);
    job.setCron(descriptor.defaultCron());
    job.setTimezone(descriptor.defaultTimezone());
    job.setParametersJson("{}");
    // Avoid running immediately on startup; the first run should follow the persisted cron.
    job.setLastCompletedAt(applicationTime.now(applicationTime.businessZone()));
    job.setLastStatus("SKIPPED");
    jobRepository.save(job);
  }
}
