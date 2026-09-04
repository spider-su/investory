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
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    LocalDate today = LocalDate.now();
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
    instance.setCreatedAt(java.time.ZonedDateTime.now());
    instance.setUpdatedAt(java.time.ZonedDateTime.now());
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
    verify(fxProvider).fetchRates(any());
  }
}
