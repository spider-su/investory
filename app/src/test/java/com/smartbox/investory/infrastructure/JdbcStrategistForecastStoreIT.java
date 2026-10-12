package com.smartbox.investory.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.smartbox.investory.app.MarketRadarOutlookTemplateTest;
import com.smartbox.investory.integrations.market.radar.JdbcStrategistForecastStore;
import com.smartbox.investory.marketradar.application.*;
import com.smartbox.investory.marketradar.application.StrategistForecastImportService.ImportRecord;
import com.smartbox.investory.marketradar.domain.StrategistForecast.SourceType;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class JdbcStrategistForecastStoreIT {
  @Test
  void retainsRevisionOnSameUrlAndIdempotentReimport() throws Exception {
    try (var db = MigrationTestDatabase.open("forecast_history")) {
      MigrationTestDatabase.migrate(db);
      var ds = new DriverManagerDataSource(db.jdbcUrl(), db.username(), db.password());
      var jdbc = new JdbcTemplate(ds);
      // Every connection receives an explicit schema; no dependency on the caller search path.
      ds.setUrl(
          db.jdbcUrl() + (db.jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=investory");
      var store = transactionalStore(ds);
      var importer =
          new StrategistForecastImportService(
              store, Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
      var first =
          row(
              "synthetic",
              "Synthetic institution",
              "Fixture strategist",
              "2026-07-01",
              "5200",
              "https://example.com/fixture",
              "Synthetic source");
      var revision =
          row(
              "synthetic",
              "Synthetic institution",
              "Fixture strategist",
              "2026-08-01",
              "5400",
              "https://example.com/fixture",
              "Revised source title");
      assertThat(importer.importForecasts(List.of(first, revision)).inserted()).isEqualTo(2);
      assertThat(importer.importForecasts(List.of(first, revision)).duplicates()).isEqualTo(2);
      var valid =
          row(
              "another",
              "Another synthetic",
              "Fixture strategist",
              "2026-08-02",
              "5000",
              "https://example.com/another",
              "Synthetic source");
      var bad =
          row(
              "synthetic",
              "Synthetic institution",
              "Fixture strategist",
              "2026-07-01",
              "5200",
              "https://example.com/fixture",
              "Changed provenance");
      assertThatThrownBy(() -> importer.importForecasts(List.of(valid, bad)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from market_radar_forecast_source where source_url = 'https://example.com/another'",
                  Integer.class))
          .isZero();
      assertThat(store.observations(LocalDate.of(2026, 12, 31))).hasSize(2);
      assertThat(store.observations(LocalDate.of(2026, 12, 31)))
          .extracting(o -> o.forecast().sourceTitle())
          .containsExactly("Synthetic source", "Revised source title");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from market_radar_forecast_observation where supersedes_id is not null",
                  Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void duplicateImportCannotRenameHistoricalInstitutionAttribution() throws Exception {
    try (var db = MigrationTestDatabase.open("forecast_attribution")) {
      MigrationTestDatabase.migrate(db);
      var ds =
          new DriverManagerDataSource(
              db.jdbcUrl() + (db.jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=investory",
              db.username(),
              db.password());
      var store = transactionalStore(ds);
      var importer =
          new StrategistForecastImportService(
              store, Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
      var original =
          row(
              "synthetic",
              "Original institution",
              "Fixture strategist",
              "2026-07-01",
              "5200",
              "https://example.com/attribution",
              "Synthetic source");
      assertThat(importer.importForecasts(List.of(original)).inserted()).isEqualTo(1);
      var conflicting =
          row(
              "synthetic",
              "Different institution",
              "Fixture strategist",
              "2026-07-01",
              "5200",
              "https://example.com/attribution",
              "Synthetic source");
      assertThatThrownBy(() -> importer.importForecasts(List.of(conflicting)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("different attribution");
      assertThat(store.observations(LocalDate.of(2026, 12, 31)))
          .hasSize(1)
          .allSatisfy(
              o -> assertThat(o.forecast().institutionName()).isEqualTo("Original institution"));
      assertThat(importer.importForecasts(List.of(original)).duplicates()).isEqualTo(1);
    }
  }

  @Test
  void squashedSchemaPreservesPublicationTitleWhenSourceTitleChanges() throws Exception {
    try (var db = MigrationTestDatabase.open("forecast_title_upgrade")) {
      MigrationTestDatabase.migrate(db);
      var ds =
          new DriverManagerDataSource(
              db.jdbcUrl() + (db.jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=investory",
              db.username(),
              db.password());
      var jdbc = new JdbcTemplate(ds);
      Long institution =
          jdbc.queryForObject(
              "insert into market_radar_forecast_institution (institution_key, display_name) values ('legacy', 'Legacy institution') returning id",
              Long.class);
      Long source =
          jdbc.queryForObject(
              "insert into market_radar_forecast_source (source_url, source_title, source_type) values ('https://example.com/legacy', 'Original source title', 'PRIMARY') returning id",
              Long.class);
      jdbc.update(
          "insert into market_radar_forecast_observation (institution_id,source_id,source_title,published_at,target_date,target_level) values (?,?,'Original source title','2026-07-01T12:00:00Z','2026-12-31',5200)",
          institution,
          source);
      jdbc.update(
          "update market_radar_forecast_source set source_title='Revised source title' where id=?",
          source);
      assertThat(new JdbcStrategistForecastStore(jdbc).observations(LocalDate.of(2026, 12, 31)))
          .hasSize(1)
          .allSatisfy(
              o -> assertThat(o.forecast().sourceTitle()).isEqualTo("Original source title"));
    }
  }

  @Test
  void numericBoundaryFitsAndOverflowRejectsBeforeAnyNewDatabaseWrite() throws Exception {
    try (var db = MigrationTestDatabase.open("forecast_numeric_boundary")) {
      MigrationTestDatabase.migrate(db);
      var ds =
          new DriverManagerDataSource(
              db.jdbcUrl() + (db.jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=investory",
              db.username(),
              db.password());
      var store = transactionalStore(ds);
      var importer =
          new StrategistForecastImportService(
              store, Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC));
      var largest =
          row(
              "boundary",
              "Boundary fixture",
              "Fixture strategist",
              "2026-07-01",
              "9999999999.9999",
              "https://example.com/boundary",
              "Synthetic source");
      assertThat(importer.importForecasts(List.of(largest)).inserted()).isEqualTo(1);
      var valid =
          row(
              "valid",
              "Valid fixture",
              "Fixture strategist",
              "2026-07-01",
              "5200",
              "https://example.com/valid-boundary",
              "Synthetic source");
      var overflow =
          row(
              "overflow",
              "Overflow fixture",
              "Fixture strategist",
              "2026-07-01",
              "10000000000",
              "https://example.com/overflow",
              "Synthetic source");
      assertThatThrownBy(() -> importer.importForecasts(List.of(valid, overflow)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("targetLevel");
      assertThat(store.observations(LocalDate.of(2026, 12, 31))).hasSize(1);
      assertThat(
              new JdbcTemplate(ds)
                  .queryForObject(
                      "select count(*) from market_radar_forecast_institution where institution_key in ('valid','overflow')",
                      Integer.class))
          .isZero();
    }
  }

  @Test
  public void rendersRealSourceAcceptanceInDisposableDevelopmentDatabase() throws Exception {
    renderRealSourceAcceptance("forecast_source_acceptance");
  }

  public void renderRealSourceAcceptance(String scope) throws Exception {
    // Public facts verified 2026-10-10; fixed QA capture on that date, never backdated.
    try (var db = MigrationTestDatabase.open(scope)) {
      MigrationTestDatabase.migrate(db);
      var ds =
          new DriverManagerDataSource(
              db.jdbcUrl() + (db.jdbcUrl().contains("?") ? "&" : "?") + "currentSchema=investory",
              db.username(),
              db.password());
      var store = transactionalStore(ds);
      var importer =
          new StrategistForecastImportService(
              store, Clock.fixed(Instant.parse("2026-10-10T18:55:00Z"), ZoneOffset.UTC));
      var gs =
          row(
              "goldman-sachs",
              "Goldman Sachs Research",
              "Ben Snider",
              "2026-05-28",
              "8000",
              "https://www.goldmansachs.com/insights/articles/s-and-p-500-forecast-to-climb-as-earnings-growth-powers-stocks-higher",
              "The S&P 500 Is Forecast to Climb as Earnings Growth Powers Stocks Higher");
      var jpm =
          row(
              "jpmorgan",
              "J.P. Morgan Global Research",
              "Dubravko Lakos-Bujas",
              "2026-07-01",
              "7800",
              "https://cws-main-ndc.jpmorgan.com/insights/global-research/outlook/mid-year-outlook",
              "Mid-year market outlook 2026: The tug of war continues");
      var ms =
          row(
              "morgan-stanley",
              "Morgan Stanley",
              "Mike Wilson",
              "2026-09-21",
              "8000",
              "https://www.morganstanley.com/insights/podcasts/thoughts-on-the-market/bull-market-resilience-isnt-complacency-mike-wilson",
              "Market Resilience Isn’t Complacency");
      assertThat(importer.importForecasts(List.of(gs, jpm, ms)).inserted()).isEqualTo(3);
      var service = new StrategistOutlookService(store, (symbol, from, to) -> List.of());
      var result = service.outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 10));
      assertThat(result.eligibleInstitutions()).isEqualTo(2);
      assertThat(result.medianTargetLevel()).isEqualTo(7900);
      assertThat(result.minTargetLevel()).isEqualTo(7800);
      assertThat(result.maxTargetLevel()).isEqualTo(8000);
      assertThat(result.actualYtdPercent()).isNull();
      assertThat(result.medianFullYearPercent()).isNull();
      assertThat(result.medianRemainingPercent()).isNull();
      assertThat(result.dispersionPercentagePoints()).isNull();
      String html = MarketRadarOutlookTemplateTest.render(result);
      assertThat(html)
          .contains(
              "Goldman Sachs Research",
              "Ben Snider",
              "2026-05-28",
              "8000",
              "J.P. Morgan Global Research",
              "Dubravko Lakos-Bujas",
              "2026-07-01",
              "7800",
              "7800.0 / 8000.0 index points",
              gs.sourceUrl(),
              jpm.sourceUrl());
      Path artifact = Path.of("target/ui-test-results/issue-112-real-source.html");
      Files.createDirectories(artifact.getParent());
      Files.writeString(
          artifact,
          html.replace(
              "<main",
              "<p>Disposable source QA: capture 2026-10-10. Sources verified 2026-10-10. No benchmark quotes supplied; returns intentionally unavailable.</p><main"));
      var today = service.outlook(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 10));
      assertThat(today.eligibleInstitutions()).isEqualTo(2);
      assertThat(today.staleExclusions()).isEqualTo(1);
      assertThat(today.medianTargetLevel()).isEqualTo(7900);
    } // Worker database is dropped. No observations in normal development/production databases.
  }

  private static com.smartbox.investory.marketradar.port.StrategistForecastStore transactionalStore(
      DriverManagerDataSource ds) {
    var proxy =
        new org.springframework.aop.framework.ProxyFactory(
            new JdbcStrategistForecastStore(new JdbcTemplate(ds)));
    proxy.addAdvice(
        new org.springframework.transaction.interceptor.TransactionInterceptor(
            new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds),
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
    return (com.smartbox.investory.marketradar.port.StrategistForecastStore) proxy.getProxy();
  }

  private static ImportRecord row(
      String key,
      String name,
      String strategist,
      String date,
      String target,
      String url,
      String title) {
    return new ImportRecord(
        key,
        name,
        strategist,
        Instant.parse(date + "T00:00:00Z"),
        LocalDate.of(2026, 12, 31),
        new BigDecimal(target),
        url,
        title,
        SourceType.PRIMARY);
  }
}
