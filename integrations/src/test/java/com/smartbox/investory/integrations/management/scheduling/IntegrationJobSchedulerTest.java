package com.smartbox.investory.integrations.management.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobRepository;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi;
import com.smartbox.investory.investment.api.operations.InvestmentMaintenanceApi.CurrencyRefreshResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Integration Job Scheduler")
class IntegrationJobSchedulerTest {

  private final IntegrationJobRepository jobs = mock();
  private final IntegrationInstanceRepository instances = mock();
  private final InvestmentMaintenanceApi investmentMaintenance = mock();
  private final JdbcTemplate jdbc = mock();
  private final Connection connection = mock();
  private final PreparedStatement tryLock = mock();
  private final PreparedStatement unlock = mock();
  private final ResultSet tryLockResult = mock();
  private final ResultSet unlockResult = mock();
  private final IntegrationJobScheduler scheduler =
      new IntegrationJobScheduler(jobs, instances, investmentMaintenance, jdbc);

  @DisplayName("skips Disabled Instance And Invalid Cron Without Taking Lock")
  @Test
  void skipsDisabledInstanceAndInvalidCronWithoutTakingLock() {
    IntegrationJobEntity disabled = job("0 0 * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, false);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(disabled));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));

    scheduler.poll();

    verify(jdbc, never()).execute(any(ConnectionCallback.class));
  }

  @DisplayName("runs Due Market Job And Records Success")
  @Test
  void runsDueMarketJobAndRecordsSuccess() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    allowLock();
    doAnswer(
            invocation -> {
              org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("STARTED");
              verify(jobs).save(job);
              return null;
            })
        .when(investmentMaintenance)
        .refreshPrices();

    scheduler.poll();

    verify(investmentMaintenance).refreshPrices();
    verify(jobs, org.mockito.Mockito.times(2)).save(job);
    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("SUCCESS");
  }

  @DisplayName("records Failed When Fx Refresh Returns Provider Failures")
  @Test
  void recordsFailedWhenFxRefreshReturnsProviderFailures() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    job.setJobType("refresh-rates");
    IntegrationInstanceEntity instance = instance(IntegrationType.FX_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    allowLock();
    when(investmentMaintenance.refreshCurrency())
        .thenReturn(
            new CurrencyRefreshResult(
                LocalDate.of(2026, 8, 24), List.of(), List.of("USD: rate limit")));

    scheduler.poll();

    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("FAILED");
    org.assertj.core.api.Assertions.assertThat(job.getLastError()).contains("rate limit");
  }

  @DisplayName("records Failed When Market Refresh Is Incomplete")
  @Test
  void recordsFailedWhenMarketRefreshIsIncomplete() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    allowLock();
    doThrow(new IllegalStateException("Market refresh incomplete: ABC"))
        .when(investmentMaintenance)
        .refreshPrices();

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

  @SuppressWarnings("unchecked")
  private void allowLock() {
    when(jdbc.execute(any(ConnectionCallback.class)))
        .thenAnswer(
            invocation ->
                ((ConnectionCallback<Void>) invocation.getArgument(0)).doInConnection(connection));
    try {
      when(connection.prepareStatement("select pg_try_advisory_lock(?)")).thenReturn(tryLock);
      when(tryLock.executeQuery()).thenReturn(tryLockResult);
      when(tryLockResult.next()).thenReturn(true);
      when(tryLockResult.getBoolean(1)).thenReturn(true);
      when(connection.prepareStatement("select pg_advisory_unlock(?)")).thenReturn(unlock);
      when(unlock.executeQuery()).thenReturn(unlockResult);
      when(unlockResult.next()).thenReturn(true);
      when(unlockResult.getBoolean(1)).thenReturn(true);
    } catch (java.sql.SQLException exception) {
      throw new AssertionError(exception);
    }
  }

  private IntegrationInstanceEntity instance(IntegrationType type, boolean enabled) {
    IntegrationInstanceEntity instance = new IntegrationInstanceEntity();
    instance.setId(3L);
    instance.setPluginType(type);
    instance.setEnabled(enabled);
    return instance;
  }
}
