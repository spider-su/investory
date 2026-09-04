package com.smartbox.investory.investment.valuation.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.smartbox.investory.integrations.fx.exchangeratehost.ExchangeRateClient;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
@DisplayName("Currency Rate Updater Postgres")
class CurrencyRateUpdaterPostgresIT extends FastDatabaseTest {

  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("currency_rate_updater");

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @Autowired private CurrencyRateUpdaterService updater;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CurrencyRateService currencyRateService;
  @MockitoBean private ExchangeRateClient client;

  @Test
  void canonicalHappyInvestorRefreshPersistsOrientedPlnEurUsdRates() {
    LocalDate date = LocalDate.of(2025, 12, 31);
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(date);
    response.setQuotes(Map.of("USDEUR", 1.173562, "USDPLN", 3.601600));
    when(client.getLatestRates(eq("USD"), eq("EUR,PLN"), anyString())).thenReturn(response);

    CurrencyRateUpdaterService.CurrencyRateRefreshResult result =
        updater.updateCurrencyRatesForDate(date);

    assertEquals(date, result.rateDate());
    assertEquals(java.util.List.of("USD", "EUR", "PLN"), result.updated());
    assertEquals(java.util.List.of(), result.failed());
    assertEquals(
        new java.math.BigDecimal("3.60160000"),
        jdbc.queryForObject(
            "select rate from investory.exchange_rates where base = 'USD' and to_currency = 'PLN' and rate_date = ? and source = 'EXCHANGERATE_HOST'",
            java.math.BigDecimal.class,
            date));
    assertEquals(
        0,
        jdbc.queryForObject(
                "select rate from investory.exchange_rates where base = 'EUR' and to_currency = 'PLN' and rate_date = ? and source = 'EXCHANGERATE_HOST'",
                java.math.BigDecimal.class,
                date)
            .compareTo(new java.math.BigDecimal("3.06894736")));

    CurrencyRateService.FxRateResolution inverse =
        currencyRateService.resolveRate(CurrencyType.EUR, CurrencyType.PLN, date);
    assertEquals("OK", inverse.conversionStatus());
    assertEquals(0, inverse.fxRateToTarget().compareTo(new java.math.BigDecimal("3.06894736")));
  }

  @DisplayName("persists Provider Date Without Identity Rows And Is Idempotent")
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
                    + "from investory.resolve_fx_rate(date '2026-08-20', ?, ?)",
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

    assertThrows(
        IllegalStateException.class,
        () -> currencyRateService.activateDailyHistoryAt(LocalDate.of(2026, 8, 19)));
    assertEquals(
        "2026-08-20",
        jdbc.queryForObject(
            "select config_value from investory.fx_configuration where config_key = 'daily_history_start'",
            String.class));
  }

  @Test
  void transactionResolutionUsesWarsawDayForDirectAndInverseRates() {
    jdbc.execute("set time zone 'UTC'");
    jdbc.update(
        "insert into investory.exchange_rates(rate_date, base, to_currency, rate, source, method, observed_at, source_reference) "
            + "values (?, 'USD', 'EUR', 0.90, 'XTB', 'XTB_EXECUTION', ?, 'tz-direct')",
        LocalDate.of(2026, 1, 2),
        java.sql.Timestamp.from(
            ZonedDateTime.of(2026, 1, 1, 23, 0, 0, 0, ZoneOffset.UTC).toInstant()));
    jdbc.update(
        "insert into investory.exchange_rates(rate_date, base, to_currency, rate, source, method, observed_at, source_reference) "
            + "values (?, 'PLN', 'EUR', 4.50, 'IBKR', 'IBKR_EXECUTION', ?, 'tz-inverse')",
        LocalDate.of(2026, 1, 2),
        java.sql.Timestamp.from(
            ZonedDateTime.of(2026, 1, 1, 23, 0, 0, 0, ZoneOffset.UTC).toInstant()));

    ZonedDateTime transaction = ZonedDateTime.of(2026, 1, 1, 23, 30, 0, 0, ZoneOffset.UTC);
    Map<String, Object> direct =
        jdbc.queryForMap(
            "select fx_rate_to_target, conversion_status from investory.resolve_transaction_fx_rate(?, 'USD', 'EUR', 'TRANSACTION')",
            java.sql.Timestamp.from(transaction.toInstant()));
    Map<String, Object> inverse =
        jdbc.queryForMap(
            "select fx_rate_to_target, conversion_status from investory.resolve_transaction_fx_rate(?, 'EUR', 'PLN', 'TRANSACTION')",
            java.sql.Timestamp.from(transaction.toInstant()));

    assertEquals("OK", direct.get("conversion_status"));
    assertEquals(
        0,
        ((java.math.BigDecimal) direct.get("fx_rate_to_target"))
            .compareTo(new java.math.BigDecimal("0.9")));
    assertEquals("OK", inverse.get("conversion_status"));
    assertEquals(
        1.0 / 4.5,
        ((java.math.BigDecimal) inverse.get("fx_rate_to_target")).doubleValue(),
        0.0000000001);
    assertEquals(
        0.9,
        currencyRateService
            .resolveTransactionRate(transaction, CurrencyType.USD, CurrencyType.EUR)
            .fxRateToTarget()
            .doubleValue(),
        0.00000001);
    assertEquals(
        1.0 / 4.5,
        currencyRateService
            .resolveTransactionRate(transaction, CurrencyType.EUR, CurrencyType.PLN)
            .fxRateToTarget()
            .doubleValue(),
        0.00000001);
  }

  @Test
  void invalidProviderDataLeavesNoPartialRefreshAndDoesNotAdvanceStart() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 20));
    response.setQuotes(new java.util.LinkedHashMap<>(Map.of("USDEUR", 0.9, "USDPLN", -4.0)));
    when(client.getLatestRates(eq("USD"), eq("EUR,PLN"), anyString())).thenReturn(response);

    int before =
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = date '2026-08-20'",
            Integer.class);
    String startBefore =
        jdbc.queryForObject(
            "select config_value from investory.fx_configuration where config_key = 'daily_history_start'",
            String.class);

    CurrencyRateUpdaterService.CurrencyRateRefreshResult result =
        updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 20));

    assertEquals(1, result.failed().size());
    assertEquals(
        before,
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = date '2026-08-20'",
            Integer.class));
    assertEquals(
        startBefore,
        jdbc.queryForObject(
            "select config_value from investory.fx_configuration where config_key = 'daily_history_start'",
            String.class));
  }

  @Test
  void missingProviderQuoteLeavesNoPartialRefreshAndDoesNotAdvanceStart() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 22));
    response.setQuotes(Map.of("USDEUR", 0.9));
    when(client.getLatestRates(eq("USD"), eq("EUR,PLN"), anyString())).thenReturn(response);

    assertInvalidRefreshLeavesDatabaseUnchanged(LocalDate.of(2026, 8, 22));
  }

  @Test
  void nonFiniteProviderQuoteLeavesNoPartialRefreshAndDoesNotAdvanceStart() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 23));
    response.setQuotes(Map.of("USDEUR", Double.NaN, "USDPLN", 4.0));
    when(client.getLatestRates(eq("USD"), eq("EUR,PLN"), anyString())).thenReturn(response);

    assertInvalidRefreshLeavesDatabaseUnchanged(LocalDate.of(2026, 8, 23));
  }

  @Test
  void futureProviderDateLeavesNoPartialRefreshAndDoesNotAdvanceStart() {
    ExchangeRateClient.ExchangeRateResponse response =
        new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 25));
    response.setQuotes(Map.of("USDEUR", 0.9, "USDPLN", 4.0));
    when(client.getLatestRates(eq("USD"), eq("EUR,PLN"), anyString())).thenReturn(response);

    assertInvalidRefreshLeavesDatabaseUnchanged(LocalDate.of(2026, 8, 24));
  }

  private void assertInvalidRefreshLeavesDatabaseUnchanged(LocalDate effectiveDate) {
    int before =
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = ?",
            Integer.class,
            effectiveDate);
    String startBefore =
        jdbc.queryForObject(
            "select config_value from investory.fx_configuration where config_key = 'daily_history_start'",
            String.class);

    CurrencyRateUpdaterService.CurrencyRateRefreshResult result =
        updater.updateCurrencyRatesForDate(effectiveDate);

    assertEquals(1, result.failed().size());
    assertEquals(
        before,
        jdbc.queryForObject(
            "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = ?",
            Integer.class,
            effectiveDate));
    assertEquals(
        startBefore,
        jdbc.queryForObject(
            "select config_value from investory.fx_configuration where config_key = 'daily_history_start'",
            String.class));
  }
}
