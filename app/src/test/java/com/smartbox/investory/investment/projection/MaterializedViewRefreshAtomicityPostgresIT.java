package com.smartbox.investory.investment.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** PostgreSQL proof of refresh transaction, reader, retry, and advisory-lock semantics. */
class MaterializedViewRefreshAtomicityPostgresIT {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("mv_refresh_atomicity");
  private static final String APP_SCHEMA = "investory";
  private static final int LOCK_KEY_1 = 2_147_483_647;
  private static final int LOCK_KEY_2 = 1_001;
  private static JdbcTemplate jdbc;
  private static PortfolioProjectionRefreshService service;

  @BeforeAll
  static void setUp() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setUrl(DATABASE.jdbcUrl());
    dataSource.setUsername(DATABASE.username());
    dataSource.setPassword(DATABASE.password());
    jdbc = new JdbcTemplate(dataSource);
    service =
        new PortfolioProjectionRefreshService(jdbc, new DataSourceTransactionManager(dataSource));
    jdbc.execute(
        "DROP MATERIALIZED VIEW IF EXISTS "
            + APP_SCHEMA
            + ".app_v_canonical_asset_daily_price_mv CASCADE");
    jdbc.execute(
        "DROP MATERIALIZED VIEW IF EXISTS "
            + APP_SCHEMA
            + ".app_v_canonical_asset_daily_price_ranked_mv CASCADE");
    jdbc.execute(
        "DROP MATERIALIZED VIEW IF EXISTS " + APP_SCHEMA + ".app_v_current_asset_price_mv CASCADE");
    jdbc.execute(
        "DROP MATERIALIZED VIEW IF EXISTS "
            + APP_SCHEMA
            + ".app_v_portfolio_daily_fx_rate_mv CASCADE");
    jdbc.execute("DROP TABLE IF EXISTS " + APP_SCHEMA + ".source CASCADE");
    jdbc.execute("CREATE TABLE " + APP_SCHEMA + ".source (value integer NOT NULL)");
    jdbc.update("INSERT INTO " + APP_SCHEMA + ".source VALUES (1)");
    jdbc.execute("CREATE TABLE " + APP_SCHEMA + ".failure_stage (name text NOT NULL)");
    jdbc.update("INSERT INTO " + APP_SCHEMA + ".failure_stage VALUES ('middle')");
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION "
            + APP_SCHEMA
            + ".fail_on_two(input integer, stage text) RETURNS integer "
            + "LANGUAGE plpgsql AS $$ BEGIN IF input = 2 AND stage = (SELECT name FROM investory.failure_stage) THEN RAISE EXCEPTION 'injected refresh failure'; END IF; RETURN input; END $$");
    createTableAndView(
        "app_v_canonical_asset_daily_price_mv",
        "SELECT investory.fail_on_two(value, 'first') AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_canonical_asset_daily_price_ranked_mv",
        "SELECT value + 10 AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_current_asset_price_mv",
        "SELECT value + 20 AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_portfolio_daily_fx_rate_mv",
        "SELECT value + 30 AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_account_statistics",
        "SELECT investory.fail_on_two(value, 'middle') + 40 AS value FROM "
            + APP_SCHEMA
            + ".source");
    createTableAndView(
        "app_v_portfolio_currency_breakdown",
        "SELECT value + 50 AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_portfolio_asset_allocation",
        "SELECT value + 60 AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_symbol_performance", "SELECT value + 70 AS value FROM " + APP_SCHEMA + ".source");
    createTableAndView(
        "app_v_portfolio_kpi_summary_mv",
        "SELECT investory.fail_on_two(value, 'last') + 80 AS value FROM " + APP_SCHEMA + ".source");
  }

  @AfterAll
  static void tearDown() {
    DATABASE.close();
  }

  @BeforeEach
  void resetFixture() {
    jdbc.update("DELETE FROM " + APP_SCHEMA + ".source");
    jdbc.update("INSERT INTO " + APP_SCHEMA + ".source VALUES (1)");
    refreshBundle();
  }

  @ParameterizedTest
  @ValueSource(strings = {"first", "middle", "last"})
  void injectedFailureRollsBackPriorViewAndReleasesLockForRetry(String stage) {
    jdbc.update("UPDATE investory.failure_stage SET name=?", stage);
    long started = System.nanoTime();
    jdbc.update("UPDATE " + APP_SCHEMA + ".source SET value = 2");
    assertThatThrownBy(() -> refreshBundle()).hasMessageContaining("injected refresh failure");
    assertThat(value("app_v_canonical_asset_daily_price_mv")).isEqualTo(1);
    assertThat(value("app_v_portfolio_daily_fx_rate_mv")).isEqualTo(31);
    assertLockIsImmediatelyAvailable();
    System.out.printf(
        "Refresh failure rollback and lock-release stage=%s durationMs=%.3f%n",
        stage, (System.nanoTime() - started) / 1_000_000.0);

    jdbc.update("UPDATE " + APP_SCHEMA + ".source SET value = 3");
    refreshBundle();
    assertThat(value("app_v_canonical_asset_daily_price_mv")).isEqualTo(3);
    assertThat(value("app_v_current_asset_price_mv")).isEqualTo(23);
    assertThat(value("app_v_portfolio_daily_fx_rate_mv")).isEqualTo(33);
  }

  @Test
  void competingRefreshWaitsForTransactionScopedAdvisoryLock() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> holder =
          pool.submit(
              () -> {
                try (Connection connection = DATABASE.openConnection();
                    Statement statement = connection.createStatement()) {
                  connection.setAutoCommit(false);
                  statement.execute(
                      "SELECT pg_advisory_xact_lock(" + LOCK_KEY_1 + ", " + LOCK_KEY_2 + ")");
                  locked.countDown();
                  release.await(5, TimeUnit.SECONDS);
                  connection.commit();
                } catch (Exception exception) {
                  throw new RuntimeException(exception);
                }
              });
      assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();
      Future<?> contender =
          pool.submit(
              () -> {
                locked.countDown();
                refreshBundle();
              });
      Thread.sleep(200);
      assertThat(contender.isDone()).isFalse();
      release.countDown();
      contender.get(5, TimeUnit.SECONDS);
      holder.get(5, TimeUnit.SECONDS);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void repeatableReadReaderSeesPreviousGenerationUntilCommit() throws Exception {
    jdbc.update("UPDATE " + APP_SCHEMA + ".source SET value = 3");
    try (Connection reader = DATABASE.openConnection();
        Statement statement = reader.createStatement()) {
      reader.setAutoCommit(false);
      reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      assertThat(read(statement, "app_v_canonical_asset_daily_price_mv")).isEqualTo(1);
      refreshBundle();
      assertThat(read(statement, "app_v_canonical_asset_daily_price_mv")).isEqualTo(1);
      reader.commit();
      assertThat(read(statement, "app_v_canonical_asset_daily_price_mv")).isEqualTo(3);
    }
  }

  private static void refreshBundle() {
    service.refreshApplicationViews(
        PortfolioProjectionRefreshService.ApplicationRefreshScope.CURRENT_MARKET_PRICE);
  }

  private static void createTableAndView(String suffix, String query) {
    String table = APP_SCHEMA + "." + suffix;
    jdbc.execute("CREATE MATERIALIZED VIEW " + table + " AS " + query + " WITH DATA");
    jdbc.execute("CREATE UNIQUE INDEX " + suffix + "_value_idx ON " + table + " (value)");
  }

  private static int value(String suffix) {
    return jdbc.queryForObject("SELECT value FROM " + APP_SCHEMA + "." + suffix, Integer.class);
  }

  private static int read(Statement statement, String suffix) throws SQLException {
    try (var result = statement.executeQuery("SELECT value FROM " + APP_SCHEMA + "." + suffix)) {
      result.next();
      return result.getInt(1);
    }
  }

  private static void assertLockIsImmediatelyAvailable() {
    assertThat(
            jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?, ?)", Boolean.class, LOCK_KEY_1, LOCK_KEY_2))
        .isTrue();
  }
}
