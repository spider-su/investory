package com.smartbox.investory.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.management.api.model.IntegrationType;
import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceRepository;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobRepository;
import com.smartbox.investory.integrations.management.scheduling.IntegrationJobScheduler;
import com.smartbox.investory.investment.port.fx.FxRateProvider;
import com.smartbox.investory.shared.time.ApplicationTime;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorTestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Persistence smoke for the scheduler's persisted-job boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class IntegrationJobExecutionIT extends FastDatabaseTest {
  @Autowired private IntegrationJobScheduler scheduler;
  @Autowired private IntegrationInstanceRepository instances;
  @Autowired private IntegrationJobRepository jobs;
  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
  @Autowired private ApplicationTime time;
  @MockitoBean private FxRateProvider fxProvider;

  @Test
  void schedulerPollIsSafeWhenNoPersistedJobsAreDue() {
    scheduler.poll();
    assertThat(jobs.findByEnabledTrue())
        .allMatch(job -> job.getLastStatus() == null || !"STARTED".equals(job.getLastStatus()));
    assertThat(instances.findAll()).isNotNull();
  }

  @Test
  void dueCanonicalFxJobExecutesProviderRefreshAndPersistsSuccess() {
    LocalDate today = time.today();
    when(fxProvider.fetchRates(any()))
        .thenReturn(
            List.of(
                new FxRateProvider.FxQuote(
                    com.smartbox.investory.shared.currency.CurrencyType.USD,
                    com.smartbox.investory.shared.currency.CurrencyType.EUR,
                    new BigDecimal("1.173562"),
                    today,
                    today),
                new FxRateProvider.FxQuote(
                    com.smartbox.investory.shared.currency.CurrencyType.USD,
                    com.smartbox.investory.shared.currency.CurrencyType.PLN,
                    new BigDecimal("3.601600"),
                    today,
                    today)));

    var instance =
        new com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity();
    instance.setPluginId("exchangerate-host");
    instance.setPluginType(IntegrationType.FX_DATA);
    instance.setEnabled(true);
    instance.setConfigJson("{\"apiKey\":\"test-secret-reference\"}");
    instance.setCreatedAt(time.now(ZoneId.of("Europe/Warsaw")));
    instance.setUpdatedAt(time.now(ZoneId.of("Europe/Warsaw")));
    instance = instances.saveAndFlush(instance);

    var job = new com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity();
    job.setIntegrationInstanceId(instance.getId());
    job.setJobType("refresh-rates");
    job.setEnabled(true);
    job.setCron("0 * * * * *");
    job.setTimezone("Europe/Warsaw");
    job.setParametersJson("{}");
    job = jobs.saveAndFlush(job);

    scheduler.poll();

    var completed = jobs.findById(job.getId()).orElseThrow();
    assertThat(completed.getLastStatus()).isEqualTo("SUCCESS");
    assertThat(completed.getLastStartedAt()).isNotNull();
    assertThat(completed.getLastCompletedAt()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "select rate from investory.exchange_rates where rate_date = ? and base = 'USD' and to_currency = 'PLN' and source = 'EXCHANGERATE_HOST'",
                BigDecimal.class,
                today))
        .isEqualByComparingTo("3.601600");
    assertThat(
            jdbc.queryForList(
                """
                select concat(
                    day::date, ':', source.id, '->', target.id, ':', fx.conversion_status)
                from generate_series(cast(? as date), cast(? as date), interval '1 day') day
                cross join investory.currencies source
                cross join investory.currencies target
                cross join lateral investory.resolve_fx_rate(day::date, source.id, target.id) fx
                where source.id <> target.id
                  and (fx.fx_rate_to_target is null
                       or fx.fx_rate_to_target <= 0
                       or fx.conversion_status = 'MISSING_RATE')
                order by day, source.id, target.id
                """,
                String.class,
                HappyInvestorTestData.HISTORY_START,
                today))
        .as("unavailable FX rates across the canonical test period")
        .isEmpty();
    verify(fxProvider).fetchRates(any());
  }
}
