package com.example.demo.services.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.example.demo.clients.currency.ExchangeRateClient;
import com.example.demo.infrastructure.CurrencyType;
import java.time.LocalDate;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

@ActiveProfiles("test-fast")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CurrencyRateUpdaterPostgresIT {

  private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
      .withDatabaseName("investory_fx_test")
      .withUsername("investory")
      .withPassword("investory");

  @Autowired private CurrencyRateUpdaterService updater;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CurrencyRateService currencyRateService;
  @MockitoBean private ExchangeRateClient client;

  @BeforeAll
  static void migrateDatabase() {
    POSTGRES.start();
    Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:sql/migration").load().migrate();
  }

  @AfterAll
  static void stopDatabase() {
    POSTGRES.stop();
  }

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void persistsProviderDateWithoutIdentityRowsAndIsIdempotent() {
    ExchangeRateClient.ExchangeRateResponse response = new ExchangeRateClient.ExchangeRateResponse();
    response.setDate(LocalDate.of(2026, 8, 20));
    response.setQuotes(Map.of("USDUSD", 1.0, "USDEUR", 0.9, "USDPLN", 4.0));
    when(client.getLatestRates(eq("USD"), eq("USD,EUR,PLN"), anyString())).thenReturn(response);

    updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 21));
    int firstCount = jdbc.queryForObject(
        "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = date '2026-08-20'",
        Integer.class);
    updater.updateCurrencyRatesForDate(LocalDate.of(2026, 8, 21));
    int secondCount = jdbc.queryForObject(
        "select count(*) from investory.exchange_rates where source = 'EXCHANGERATE_HOST' and method = 'MARKET_DAILY' and rate_date = date '2026-08-20'",
        Integer.class);

    assertEquals(6, firstCount);
    assertEquals(firstCount, secondCount);
    assertEquals(0, jdbc.queryForObject("select count(*) from investory.exchange_rates where base = to_currency", Integer.class));
    assertEquals(4.0, jdbc.queryForObject(
        "select rate from investory.exchange_rates where base = 'USD' and to_currency = 'PLN' and rate_date = date '2026-08-20'",
        Double.class));

    for (CurrencyType source : CurrencyType.values()) {
      for (CurrencyType target : CurrencyType.values()) {
        if (source == target) continue;
        Map<String, Object> sql = jdbc.queryForMap(
            "select fx_rate_to_target, rate_source, rate_method, source_rate_date, age_days, conversion_status "
                + "from investory.resolve_fx_rate(date '2026-08-20', ?, ?, 'VALUATION')",
            source.name(), target.name());
        CurrencyRateService.FxRateResolution java = currencyRateService.resolveRate(
            source, target, LocalDate.of(2026, 8, 20));
        assertEquals(sql.get("rate_source"), java.rateSource());
        assertEquals(sql.get("rate_method"), java.rateMethod());
        assertEquals(((java.sql.Date) sql.get("source_rate_date")).toLocalDate(), java.sourceRateDate());
        assertEquals(sql.get("age_days"), java.ageDays());
        assertEquals(sql.get("conversion_status"), java.conversionStatus());
        assertEquals(0, ((java.math.BigDecimal) sql.get("fx_rate_to_target")).compareTo(java.fxRateToTarget()));
      }
    }
  }
}
