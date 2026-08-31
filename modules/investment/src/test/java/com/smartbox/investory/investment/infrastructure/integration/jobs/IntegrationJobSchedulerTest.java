package com.smartbox.investory.investment.infrastructure.integration.jobs;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.investment.infrastructure.integration.IntegrationType;
import com.smartbox.investory.investment.infrastructure.integration.config.IntegrationConfigurationService;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationJobEntity;
import com.smartbox.investory.investment.infrastructure.integration.persistence.IntegrationJobRepository;
import com.smartbox.investory.investment.market.fx.CurrencyRateUpdaterService;
import com.smartbox.investory.investment.market.fx.CurrencyRateUpdaterService.CurrencyRateRefreshResult;
import com.smartbox.investory.investment.market.price.MarketService;
import java.time.LocalDate;
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
    IntegrationJobEntity disabled = job("0 0 * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, false);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(disabled));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));

    scheduler.poll();

    verify(jdbc, never())
        .queryForObject(eq("select pg_try_advisory_lock(?)"), eq(Boolean.class), anyLong());
  }

  @Test
  void runsDueMarketJobAndRecordsSuccess() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
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

  @Test
  void recordsFailedWhenFxRefreshReturnsProviderFailures() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    job.setJobType("refresh-rates");
    IntegrationInstanceEntity instance = instance(IntegrationType.FX_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    when(jdbc.queryForObject(eq("select pg_try_advisory_lock(?)"), eq(Boolean.class), anyLong()))
        .thenReturn(true);
    when(jdbc.queryForObject(eq("select pg_advisory_unlock(?)"), eq(Boolean.class), anyLong()))
        .thenReturn(true);
    when(fx.updateCurrencyRatesForDate(
            org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new CurrencyRateRefreshResult(
                LocalDate.of(2026, 8, 24), List.of(), List.of("USD: rate limit")));

    scheduler.poll();

    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("FAILED");
    org.assertj.core.api.Assertions.assertThat(job.getLastError()).contains("rate limit");
  }

  @Test
  void recordsFailedWhenMarketRefreshIsIncomplete() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    when(jdbc.queryForObject(eq("select pg_try_advisory_lock(?)"), eq(Boolean.class), anyLong()))
        .thenReturn(true);
    when(jdbc.queryForObject(eq("select pg_advisory_unlock(?)"), eq(Boolean.class), anyLong()))
        .thenReturn(true);
    doThrow(new IllegalStateException("Market refresh incomplete: ABC"))
        .when(market)
        .updateStocks();

    scheduler.poll();

    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("FAILED");
    org.assertj.core.api.Assertions.assertThat(job.getLastError())
        .contains("Market refresh incomplete");
  }

  private IntegrationJobEntity job(String cron, String timezone) {
    IntegrationJobEntity job = new IntegrationJobEntity();
    job.setId(2L);
    job.setIntegrationInstanceId(3L);
    job.setJobType("refresh-prices");
    job.setEnabled(true);
    job.setCron(cron);
    job.setTimezone(timezone);
    return job;
  }

  private IntegrationInstanceEntity instance(IntegrationType type, boolean enabled) {
    IntegrationInstanceEntity instance = new IntegrationInstanceEntity();
    instance.setId(3L);
    instance.setPluginType(type);
    instance.setEnabled(enabled);
    return instance;
  }
}
