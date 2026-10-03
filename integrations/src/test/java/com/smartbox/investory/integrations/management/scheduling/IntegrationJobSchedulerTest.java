package com.smartbox.investory.integrations.management.scheduling;

import static com.smartbox.investory.integrations.FixedTestTime.TIME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.application.handler.RefreshFxJobHandler;
import com.smartbox.investory.integrations.management.application.handler.RefreshPricesJobHandler;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
  private final IntegrationJobHandlerRegistry handlers = mock();
  private final IntegrationJobAlertPublisher alerts = mock();
  private final Connection connection = mock();
  private final PreparedStatement tryLock = mock();
  private final PreparedStatement unlock = mock();
  private final ResultSet tryLockResult = mock();
  private final ResultSet unlockResult = mock();
  private final IntegrationJobScheduler scheduler =
      new IntegrationJobScheduler(jobs, instances, handlers, TIME, jdbc, alerts, 360);

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
    when(handlers.require(IntegrationType.MARKET_DATA, "refresh-prices"))
        .thenReturn(new RefreshPricesJobHandler(investmentMaintenance));
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
    when(handlers.require(IntegrationType.FX_DATA, "refresh-rates"))
        .thenReturn(new RefreshFxJobHandler(investmentMaintenance));
    when(investmentMaintenance.refreshCurrency())
        .thenReturn(
            new CurrencyRefreshResult(
                LocalDate.of(2026, 8, 24), List.of(), List.of("USD: rate limit")));

    scheduler.poll();

    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("FAILED");
    org.assertj.core.api.Assertions.assertThat(job.getLastError()).contains("rate limit");
    verify(alerts).failed(job, instance, TIME.now(TIME.businessZone()), "USD: rate limit");
  }

  @DisplayName("records Failed When Market Refresh Is Incomplete")
  @Test
  void recordsFailedWhenMarketRefreshIsIncomplete() {
    IntegrationJobEntity job = job("* * * * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    allowLock();
    when(handlers.require(IntegrationType.MARKET_DATA, "refresh-prices"))
        .thenReturn(new RefreshPricesJobHandler(investmentMaintenance));
    doThrow(new IllegalStateException("Market refresh incomplete: ABC"))
        .when(investmentMaintenance)
        .refreshPrices();

    scheduler.poll();

    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("FAILED");
    org.assertj.core.api.Assertions.assertThat(job.getLastError())
        .contains("Market refresh incomplete");
    verify(alerts)
        .failed(job, instance, TIME.now(TIME.businessZone()), "Market refresh incomplete: ABC");
  }

  @DisplayName("alerts When A Configured Refresh Job Is Stale")
  @Test
  void alertsWhenConfiguredRefreshJobIsStale() {
    IntegrationJobEntity job = job("0 15 6 * * *", "Europe/Warsaw");
    job.setLastCompletedAt(ZonedDateTime.of(2026, 9, 3, 6, 15, 0, 0, ZoneId.of("Europe/Warsaw")));
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
    when(jobs.findByEnabledTrue()).thenReturn(List.of(job));
    when(instances.findById(anyLong())).thenReturn(Optional.of(instance));
    allowLock();
    when(handlers.require(IntegrationType.MARKET_DATA, "refresh-prices"))
        .thenReturn(new RefreshPricesJobHandler(investmentMaintenance));

    scheduler.poll();

    verify(alerts)
        .stale(
            job, instance, ZonedDateTime.of(2026, 9, 4, 6, 15, 0, 0, ZoneId.of("Europe/Warsaw")));
  }

  @DisplayName("records And Alerts Failed Manual Reruns")
  @Test
  void recordsAndAlertsFailedManualReruns() {
    IntegrationJobEntity job = job("0 15 6 * * *", "Europe/Warsaw");
    IntegrationInstanceEntity instance = instance(IntegrationType.MARKET_DATA, true);
    instance.setPluginId("market-plugin");
    when(instances.findByOwnerIdAndPluginIdAndPluginType(
            null, "market-plugin", IntegrationType.MARKET_DATA))
        .thenReturn(Optional.of(instance));
    when(jobs.findByIntegrationInstanceId(3L)).thenReturn(List.of(job));
    when(handlers.require(IntegrationType.MARKET_DATA, "refresh-prices"))
        .thenReturn(new RefreshPricesJobHandler(investmentMaintenance));
    doThrow(new IllegalStateException("Provider timeout"))
        .when(investmentMaintenance)
        .refreshPrices();
    allowLock();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> scheduler.runNow(IntegrationType.MARKET_DATA, "market-plugin", "refresh-prices"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Provider timeout");

    verify(jobs, times(2)).save(job);
    org.assertj.core.api.Assertions.assertThat(job.getLastStatus()).isEqualTo("FAILED");
    verify(alerts).failed(job, instance, TIME.now(TIME.businessZone()), "Provider timeout");
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
