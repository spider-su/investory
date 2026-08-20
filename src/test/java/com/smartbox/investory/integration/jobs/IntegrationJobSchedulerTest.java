package com.smartbox.investory.integration.jobs;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integration.IntegrationType;
import com.smartbox.investory.integration.config.IntegrationConfigurationService;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationInstance;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationJob;
import com.smartbox.investory.integration.infrastructure.persistence.IntegrationJobRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.market.price.MarketService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IntegrationJobSchedulerTest {

  private final IntegrationJobRepository jobs = mock();
  private final IntegrationInstanceRepository instances = mock();
  private final CurrencyRateUpdaterService fx = mock();
  private final MarketService market = mock();
  private final JdbcTemplate jdbc = mock();
  private final IntegrationConfigurationService config = mock();
  private final IntegrationJobScheduler scheduler =
      new IntegrationJobScheduler(jobs, instances, fx, market, jdbc, config);

  @Test
  void skipsDisabledInstanceAndInvalidCronWithoutTakingLock() {
    IntegrationJob disabled = job("0 0 * * * *", "Europe/Warsaw");
    IntegrationInstance instance = instance(IntegrationType.MARKET_DATA, false);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(disabled));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));

    scheduler.poll();

    verify(jdbc, never())
        .queryForObject(eq("select pg_try_advisory_lock(?)"), eq(Boolean.class), anyLong());
  }

  @Test
  void runsDueMarketJobAndRecordsSuccess() {
    IntegrationJob job = job("* * * * * *", "Europe/Warsaw");
    IntegrationInstance instance = instance(IntegrationType.MARKET_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    when(jdbc.queryForObject(eq("select pg_try_advisory_lock(?)"), eq(Boolean.class), anyLong()))
        .thenReturn(true);
    when(jdbc.queryForObject(eq("select pg_advisory_unlock(?)"), eq(Boolean.class), anyLong()))
        .thenReturn(true);

    scheduler.poll();

    verify(market).updateStocks();
    verify(jobs, org.mockito.Mockito.times(2)).save(job);
    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("SUCCESS");
  }

  private IntegrationJob job(String cron, String timezone) {
    IntegrationJob job = new IntegrationJob();
    job.setId(2L);
    job.setIntegrationInstanceId(3L);
    job.setJobType("refresh-prices");
    job.setEnabled(true);
    job.setCron(cron);
    job.setTimezone(timezone);
    return job;
  }

  private IntegrationInstance instance(IntegrationType type, boolean enabled) {
    IntegrationInstance instance = new IntegrationInstance();
    instance.setId(3L);
    instance.setPluginType(type);
    instance.setEnabled(enabled);
    return instance;
  }
}
