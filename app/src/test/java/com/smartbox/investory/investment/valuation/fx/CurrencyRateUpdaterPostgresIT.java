package com.smartbox.investory.investment.valuation.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.fx.client.ExchangeRateClient;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class CurrencyRateUpdaterPostgresIT extends FastDatabaseTest {

  @Autowired private CurrencyRateUpdaterService updater;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CurrencyRateService currencyRateService;
  @MockitoBean private ExchangeRateClient client;

  @Test
  void persistsProviderDateWithoutIdentityRowsAndIsIdempotent() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 20));
    response.setQuotes(Map.of("USDEUR", 0.9, "USDPLN", 4.0));
    when(client.getLatestRates(eq("USD"), eq("EUR,PLN"), anyString())).thenReturn(response);

    updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 21));
    int firstCount =
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = date '2026-08-20'",
            Integer.class);
    updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 21));
    int secondCount =
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = date '2026-08-20'",
            Integer.class);

    assertEquals(6, firstCount);
    assertEquals(firstCount, secondCount);
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where base = to_currency",
            Integer.class));
    assertEquals(
        4.0,
        jdbc.queryForObject(
            "select rate from investory.exchange_rates where base = 'USD' and to_currency = 'PLN' and rate_date = date '2026-08-20'",
            Double.class));
    assertEquals(
        "2026-08-20",
        jdbc.queryForObject(
            "select config_value from investory.fx_configuration where config_key = 'daily_history_start'",
            String.class));
    for (CurrencyType source : CurrencyType.values()) {
      for (CurrencyType target : CurrencyType.values()) {
        if (source == target) continue;
        Map<String, Object> sql =
            jdbc.queryForMap(
                "select fx_rate_to_target, rate_source, rate_method, source_rate_date, age_days, conversion_status "
                    + "from investory.resolve_fx_rate(date '2026-08-20', ?, ?, 'VALUATION')",
                source.name(),
                target.name());
        CurrencyRateService.FxRateResolution java =
            currencyRateService.resolveRate(source, target, LocalDate.of(2026, 8, 20));
        assertEquals(sql.get("rate_source"), java.rateSource());
        assertEquals(sql.get("rate_method"), java.rateMethod());
        assertEquals(
            ((java.sql.Date) sql.get("source_rate_date")).toLocalDate(), java.sourceRateDate());
        assertEquals(sql.get("age_days"), java.ageDays());
        assertEquals(sql.get("conversion_status"), java.conversionStatus());
        assertEquals(
            0,
            ((java.math.BigDecimal) sql.get("fx_rate_to_target")).compareTo(java.fxRateToTarget()));
      }
    }

    // PostgreSQL marks the current transaction aborted after a constraint-triggered error.
    // Keep this negative assertion last so no subsequent JDBC statement reuses that transaction.
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "update investory.fx_configuration set config_value = '2026-08-19' where config_key = 'daily_history_start'"));
  }
}
