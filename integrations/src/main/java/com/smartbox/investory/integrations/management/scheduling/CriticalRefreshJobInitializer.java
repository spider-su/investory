package com.smartbox.investory.integrations.management.scheduling;

import com.smartbox.investory.integrations.fx.nbp.NbpFxDataPlugin;
import com.smartbox.investory.integrations.management.api.model.IntegrationJobDescriptor;
import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes the persisted integration scheduler the single owner of the two critical daily market-data
 * refreshes. Existing rows are normalized to the canonical production schedule so an old/custom
 * early FX job cannot silently replace the post-publication refresh.
 */
@Component
@RequiredArgsConstructor
public class CriticalRefreshJobInitializer {
  private static final List<JobSpec> JOBS =
      List.of(
          new JobSpec(IntegrationType.FX_DATA, NbpFxDataPlugin.ID, "refresh-rates"),
          new JobSpec(IntegrationType.MARKET_DATA, null, "refresh-prices"));

  private final IntegrationInstanceRepository instanceRepository;
  private final IntegrationJobRepository jobRepository;
  private final IntegrationJobHandlerRegistry handlerRegistry;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void ensureCriticalJobs() {
    for (IntegrationInstanceEntity instance : instanceRepository.findAll()) {
      if (!instance.isEnabled()) continue;
      JOBS.stream()
          .filter(spec -> spec.type() == instance.getPluginType())
          .filter(spec -> spec.pluginId() == null || spec.pluginId().equals(instance.getPluginId()))
          .filter(spec -> handlerRegistry.supports(spec.type(), spec.jobType()))
          .forEach(spec -> ensure(instance, spec));
    }
  }

  private void ensure(IntegrationInstanceEntity instance, JobSpec spec) {
    IntegrationJobDescriptor descriptor = IntegrationJobDescriptor.forType(spec.jobType());
    IntegrationJobEntity job =
        jobRepository.findByIntegrationInstanceId(instance.getId()).stream()
            .filter(candidate -> spec.jobType().equals(candidate.getJobType()))
            .findFirst()
            .orElseGet(IntegrationJobEntity::new);
    job.setIntegrationInstanceId(instance.getId());
    job.setJobType(spec.jobType());
    job.setEnabled(true);
    job.setCron(descriptor.defaultCron());
    job.setTimezone(descriptor.defaultTimezone());
    if (job.getParametersJson() == null) job.setParametersJson("{}");
    jobRepository.save(job);
  }

  private record JobSpec(IntegrationType type, String pluginId, String jobType) {}
}
