package com.example.demo.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Migration chain test. It always runs against a disposable PostgreSQL container, never against a
 * developer database, so the destructive schema reset below cannot touch real data.
 */
@Testcontainers
class SchemaMigrationCheckpoint2Test {

  private static final String TEST_DATABASE_NAME = "investory_migration_test";

  @Container
  private final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName(TEST_DATABASE_NAME)
          .withUsername("investory_test")
          .withPassword("investory_test");

  private String dbUrl() {
    return postgres.getJdbcUrl();
  }

  private String dbUsername() {
    return postgres.getUsername();
  }

  private String dbPassword() {
    return postgres.getPassword();
  }

  private Connection openConnection() throws Exception {
    return DriverManager.getConnection(dbUrl(), dbUsername(), dbPassword());
  }

  /**
   * Hard safety guard: refuse any destructive statement unless the target is the throwaway
   * container database created for this test class.
   */
  private void assertDisposableTestDatabase() {
    String url = dbUrl();
    if (url == null || !url.matches("^jdbc:postgresql://.*/" + TEST_DATABASE_NAME + "(?:\\?.*)?$")) {
      throw new IllegalStateException(
          "Refusing to run migration test against non-disposable database: " + url);
    }
  }

  @BeforeEach
  void recreateDatabaseFromEmptySchema() throws Exception {
    assertDisposableTestDatabase();


    Flyway flyway =
        Flyway.configure()
            .cleanDisabled(true)
            .dataSource(dbUrl(), dbUsername(), dbPassword())
            .schemas("investory")
            .defaultSchema("investory")
            .locations("classpath:sql/migration")
            .load();
    flyway.migrate();
  }

  @Test
  void appliesAllMigrationsAndCreatesCheckpoint2Invariants() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(5, singleInt(statement, "SELECT count(*) FROM investory.flyway_schema_history"));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'investory'
                AND table_name = 'asset_types'
              """));
      assertEquals(
          11,
          singleInt(
              statement,
              """
              SELECT count(*)
              FROM investory.asset_types
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'investory'
                AND table_name = 'accounts'
                AND column_name = 'portfolio_id'
                AND is_nullable = 'NO'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
              JOIN pg_namespace n ON n.oid = t.relnamespace
              WHERE n.nspname = 'investory'
                AND t.relname = 'currencies'
                AND c.conname = 'chk_currencies_id_uppercase_iso'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_constraint c
              JOIN pg_class t ON t.oid = c.conrelid
              JOIN pg_namespace n ON n.oid = t.relnamespace
              WHERE n.nspname = 'investory'
                AND t.relname = 'asset_source_symbols'
                AND c.conname = 'chk_asset_source_symbols_price_scale_factor_positive'
              """));
      assertTrue(viewExists(statement, "v_normalized_daily_price"));
      assertTrue(viewExists(statement, "v_reconstructed_position_daily"));
      assertTrue(viewExists(statement, "v_position_valuation_validation"));
      assertTrue(viewExists(statement, "v_account_daily_reconciliation"));
      assertTrue(viewExists(statement, "v_non_usd_closed_trade_reconciliation"));
      assertTrue(
          materializedViewExists(statement, "reporting_trade_settlement_reconciliation"));
      assertTrue(viewExists(statement, "reporting_trade_settlement_reconciliation_by_account"));
      assertTrue(viewExists(statement, "v_reporting_validation_summary"));
      assertTrue(viewExists(statement, "v_position_currency_validation"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "selection_priority"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "selected_price_date"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "underlying_observation_date"));
      assertTrue(columnExists(statement, "v_normalized_daily_price", "price_age_days"));
      assertTrue(columnExists(statement, "v_reconstructed_position_daily", "contract_multiplier"));
      assertTrue(columnExists(statement, "v_reconstructed_position_daily", "selected_price_date"));
      assertTrue(columnExists(statement, "v_account_daily_reconciliation", "market_value_difference"));
      assertTrue(columnExists(statement, "v_non_usd_closed_trade_reconciliation", "anomaly_code"));
      assertTrue(
          relationColumnExists(
              statement, "reporting_trade_settlement_reconciliation", "settlement_model"));
      assertTrue(
          relationColumnExists(
              statement, "reporting_trade_settlement_reconciliation", "missing_fx_count"));
      assertTrue(
          relationColumnExists(
              statement, "reporting_trade_settlement_reconciliation", "is_complete"));
      assertTrue(
          relationColumnExists(
              statement, "reporting_trade_settlement_reconciliation", "anomaly_code"));
      assertTrue(
          relationColumnExists(
              statement, "reporting_trade_settlement_reconciliation", "position_close_result_base"));
      assertTrue(
          relationColumnExists(
              statement, "reporting_trade_settlement_reconciliation", "carried_close_quantity"));
      assertTrue(
          relationColumnExists(
              statement,
              "reporting_trade_settlement_reconciliation",
              "same_day_round_trip_quantity"));
      assertTrue(
          relationColumnExists(
              statement,
              "reporting_trade_settlement_reconciliation",
              "result_settlement_difference_base"));
      assertTrue(columnExists(statement, "positions", "settlement_model"));
      assertTrue(columnExists(statement, "positions", "broker_product"));
      assertTrue(columnExists(statement, "positions", "source_position_id"));
      assertTrue(columnExists(statement, "positions", "source_row_occurrence"));
      assertTrue(columnExists(statement, "positions", "source_open_price"));
      assertTrue(columnExists(statement, "positions", "source_close_price"));
      assertTrue(columnExists(statement, "positions", "open_conversion_rate"));
      assertTrue(columnExists(statement, "positions", "close_conversion_rate"));
      assertTrue(columnExists(statement, "v_reporting_validation_summary", "status"));
      assertTrue(columnExists(statement, "v_open_position_values", "account_currency"));
      assertTrue(columnExists(statement, "v_open_position_values", "position_row_count"));
      assertTrue(columnExists(statement, "v_position_currency_validation", "anomaly_code"));
      assertEquals(
          "bigint",
          singleString(
              statement,
              """
              select data_type from information_schema.columns
              where table_schema = 'investory' and table_name = 'positions' and column_name = 'asset_id'
              """));
      assertEquals(
          "bigint",
          singleString(
              statement,
              """
              select data_type from information_schema.columns
              where table_schema = 'investory' and table_name = 'cash_operations' and column_name = 'asset_id'
              """));
      assertFalse(columnExists(statement, "positions", "currency"));
      assertTrue(
          singleInt(
                  statement,
                  """
                  select count(*)
                  from investory.asset_price_history aph
                  join investory.assets a on a.id = aph.asset_id
                  where a.symbol = 'JGPI'
                    and aph.price_origin in
                        ('XTB_TRADE_OPEN', 'XTB_TRADE_CLOSE', 'INTERPOLATED_XTB')
                  """)
              > 0);
      assertEquals(
          "Apple",
          singleString(
              statement,
              """
              select name from investory.assets where symbol = 'AAPL.US'
              """));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select currency from investory.assets where symbol = 'NCLR.UK'
              """));
      assertEquals(
          0,
          singleInt(
              statement,
              """
              select count(*)
              from investory.asset_price_history aph
              join investory.assets a on a.id = aph.asset_id
              where a.symbol = 'NCLR.UK' and aph.price_currency <> 'USD'
              """));
    }
  }

  @Test
  void classifiesIbkrCashTransferWithEnrichedCommentAsExternalDeposit() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.cash_operations (
              id, account_id, operation, asset_id, amount, currency, comment, date
          ) values (
              -900001, 17959259, 'TRANSFER', null, 7838.285, 'USD',
              'Cash Transfer | ibkrRawType=Deposit | ibkrRawSymbol=- | ibkrGrossAmount=7838.285',
              timestamptz '2025-02-12 00:00:00+01:00'
          )
          """);
      assertEquals(
          "EXTERNAL_DEPOSIT",
          singleString(
              statement,
              """
              select normalized_category
              from investory.normalized_cash_operations
              where operation_id = -900001
              """));
    }
  }

  @Test
  void storesCspxAsUsdAcrossAssetMappingAndHistory() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select currency
              from investory.assets
              where symbol = 'CSPX.UK'
              """));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select price_currency
              from investory.asset_source_symbols
              where asset_id = (select id from investory.assets where symbol = 'CSPX.UK')
                and source = 'STOOQ'
                and source_symbol = 'cspx.uk'
              """));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select price_currency
              from investory.asset_price_history
              where asset_id = (select id from investory.assets where symbol = 'CSPX.UK')
                and source = 'STOOQ'
                and source_symbol = 'cspx.uk'
                and price_date = date '2025-11-26'
              """));
    }
  }

  @Test
  void advancesPortfolioSequencePastSeededIds() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          2,
          singleInt(
              statement,
              """
              insert into investory.portfolios (name, base_currency)
              values ('Sequence regression portfolio', 'USD')
              returning id
              """));
    }
  }

  @Test
  void convertsAccountDailyValuesToPortfolioBaseCurrency() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency)
          values (-920001, 'EUR reporting portfolio', 'EUR');
          insert into investory.accounts (id, currency, provider, name, owner, portfolio_id)
          values (-920001, 'USD', 'IBKR', 'USD account', 'Test', -920001);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values (-920001, date '2025-01-15', 'USD', 100, 50, 150)
          """);

      assertEquals(
          "144.92753623",
          singleString(
              statement,
              """
              select trim(to_char(round(equity::numeric, 8), 'FM9999999990.00000000'))
              from investory.v_portfolio_daily
              where portfolio_id = -920001
              """));
    }
  }

  @Test
  void requiresCoreRawRowFields() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          12,
          singleInt(
              statement,
              """
              select count(*)
              from information_schema.columns
              where table_schema = 'investory'
                and is_nullable = 'NO'
                and (
                    (table_name = 'cash_operations' and column_name in ('account_id', 'currency'))
                    or
                    (table_name = 'positions' and column_name in
                        ('account_id', 'asset_id', 'source_asset_symbol', 'volume', 'open_time',
                         'open_price', 'price_currency', 'cost_currency', 'profit_currency',
                         'commission_currency'))
                )
              """));
    }
  }

  @Test
  void sameCurrencyPositionDoesNotReportMissingFx() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency)
          values (-930001, 'USD FX regression portfolio', 'USD');
          insert into investory.accounts (id, currency, provider, name, owner, portfolio_id)
          values (-930001, 'USD', 'IBKR', 'USD account', 'Test', -930001);
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, profit
          ) values (
              -930001, -930001, (select id from investory.assets where symbol = 'AAPL.US'),
              'AAPL', 'BUY', 1, 'USD', 'USD', 'USD', 'USD',
              timestamptz '2025-01-01 00:00:00+00', 100, 10
          );
          select investory.refresh_reporting_views()
          """);

      assertEquals(
          0,
          singleInt(
              statement,
              """
              select fallback_position_fx_missing_count
              from investory.v_portfolio_service_fallback_reconciliation
              where portfolio_id = -930001
              """));
    }
  }

  @Test
  void keepsUsdCostCurrencyForUsdAssetHeldInPlnAccounts() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          update investory.assets
             set market_price = 634.50
           where symbol = 'MSFT.US'
          """);

      statement.execute(
          """
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, purchase_value
          ) values
              (-910001, 17959259, (select id from investory.assets where symbol = 'MSFT.US'), 'MSFT',
               'BUY', 3, 'USD', 'USD', 'USD', 'USD', timestamptz '2025-07-01 12:00:00+00', 402.91, 1208.73),
              (-910002, 50290466, (select id from investory.assets where symbol = 'MSFT.US'), 'MSFT',
               'BUY', 5, 'USD', 'USD', 'USD', 'USD', timestamptz '2025-07-02 12:00:00+00', 454.422, 2272.11),
              (-910003, 51707603, (select id from investory.assets where symbol = 'MSFT.US'), 'MSFT',
               'BUY', 5, 'USD', 'USD', 'USD', 'USD', timestamptz '2025-07-03 12:00:00+00', 454.716, 2273.58)
          """);

      assertEquals(0, singleInt(statement, """
          select count(*)
          from investory.v_position_currency_validation
          where asset_id = (select id from investory.assets where symbol = 'MSFT.US')
            and anomaly_code = 'POSITION_ASSET_CURRENCY_MISMATCH'
          """));

      assertEquals(0, singleInt(statement, "select investory.repair_position_trade_currency()"));

      assertEquals(
          "USD",
          singleString(statement, """
              select cost_currency
              from investory.positions
              where id = -910002
              """));
      assertEquals(
          "USD",
          singleString(statement, """
              select cost_currency
              from investory.positions
              where id = -910003
              """));

      assertEquals(
          "2272.11000000",
          singleString(
              statement,
              """
              select trim(to_char(round(cost_basis_in_base_currency::numeric, 8), 'FM9999999990.00000000'))
              from investory.v_open_position_values
              where asset_symbol = 'MSFT.US'
                and account_id = 50290466
              """));

      assertEquals(
          "5754.42000000",
          singleString(
              statement,
              """
              select trim(to_char(round(sum(cost_basis_in_base_currency)::numeric, 8), 'FM9999999990.00000000'))
              from investory.v_open_position_values
              where asset_symbol = 'MSFT.US'
              """));
    }
  }

  @Test
  void canonicalPortfolioFxSupportsSameCurrencyDirectInverseTriangulationStaleAndMissing()
      throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          delete from investory.exchange_rates;

          insert into investory.exchange_rates (month, source, base, to_currency, rate)
          values
            (date '2025-01-01', 'TEST', 'USD', 'EUR', 0.8),
            (date '2025-01-01', 'TEST', 'USD', 'PLN', 4.0);

          insert into investory.portfolios (id, name, base_currency)
          values (-940001, 'USD FX test portfolio', 'USD'),
                 (-940002, 'EUR FX test portfolio', 'EUR');

          insert into investory.accounts (id, currency, provider, name, owner, portfolio_id)
          values (-940001, 'USD', 'IBKR', 'USD FX account', 'Test', -940001),
                 (-940002, 'EUR', 'IBKR', 'EUR FX account', 'Test', -940002);

          insert into investory.account_daily (
            account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values
            (-940001, date '2025-01-15', 'USD', 0, 0, 0),
            (-940001, date '2025-04-20', 'USD', 0, 0, 0),
            (-940001, date '2024-01-10', 'USD', 0, 0, 0),
            (-940002, date '2025-01-15', 'EUR', 0, 0, 0);
          """);

      assertEquals(
          "SAME_CURRENCY",
          singleString(
              statement,
              """
              select conversion_status
              from investory.v_reporting_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2025-01-15'
                and from_currency = 'USD'
              """));

      assertEquals(
          "1.25000000",
          singleString(
              statement,
              """
              select trim(to_char(round(fx_rate_to_base::numeric, 8), 'FM9999999990.00000000'))
              from investory.v_reporting_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2025-01-15'
                and from_currency = 'EUR'
              """));

      assertEquals(
          "0.80000000",
          singleString(
              statement,
              """
              select trim(to_char(round(fx_rate_to_base::numeric, 8), 'FM9999999990.00000000'))
              from investory.v_reporting_daily_fx_rate
              where portfolio_id = -940002
                and valuation_date = date '2025-01-15'
                and from_currency = 'USD'
              """));

      assertEquals(
          "0.20000000",
          singleString(
              statement,
              """
              select trim(to_char(round(fx_rate_to_base::numeric, 8), 'FM9999999990.00000000'))
              from investory.v_reporting_daily_fx_rate
              where portfolio_id = -940002
                and valuation_date = date '2025-01-15'
                and from_currency = 'PLN'
              """));

      assertEquals(
          "STALE",
          singleString(
              statement,
              """
              select conversion_status
              from investory.v_reporting_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2025-04-20'
                and from_currency = 'EUR'
              """));

      assertEquals(
          "MISSING",
          singleString(
              statement,
              """
              select conversion_status
              from investory.v_reporting_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2024-01-10'
                and from_currency = 'EUR'
              """));
    }
  }

  @Test
  void accountStatisticsFailClosedWhenOneCashOperationHasMissingFx() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          delete from investory.exchange_rates;
          insert into investory.exchange_rates (month, source, base, to_currency, rate)
          values (date '2025-01-01', 'TEST', 'USD', 'EUR', 0.8);

          insert into investory.portfolios (id, name, base_currency)
          values (-950001, 'Missing FX portfolio', 'USD');

          insert into investory.accounts (id, currency, provider, name, owner, portfolio_id)
          values (-950001, 'USD', 'IBKR', 'Missing FX account', 'Test', -950001);

          insert into investory.cash_operations (id, account_id, operation, asset_id, amount, currency, comment, date)
          values
            (-950101, -950001, 'DEPOSIT', null, 100.00, 'USD', 'usd deposit', timestamptz '2025-01-15 00:00:00+00'),
            (-950102, -950001, 'DEPOSIT', null, 80.00, 'EUR', 'eur deposit', timestamptz '2025-01-15 00:00:00+00'),
            (-950103, -950001, 'DEPOSIT', null, 400.00, 'PLN', 'pln deposit missing fx', timestamptz '2025-01-15 00:00:00+00');

          insert into investory.account_daily (
            account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values (-950001, date '2025-01-15', 'USD', 0, 0, 0);

          select investory.refresh_reporting_views();
          """);

      assertEquals(
          1,
          singleInt(
              statement,
              """
              select count(*)
              from investory.normalized_cash_operations
              where account_id = -950001
                and portfolio_conversion_status = 'MISSING'
              """));

      assertEquals(
          1,
          singleInt(
              statement,
              """
              select missing_fx_count
              from investory.account_statistics
              where account_id = -950001
              """));

      assertEquals(
          true,
          exists(
              statement,
              """
              select 1
              from investory.account_statistics
              where account_id = -950001
                and total_deposit is null
                and net_deposit is null
              """));

      assertEquals(
          "200.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(converted_cash_subtotal::numeric, 8), 'FM9999999990.00000000'))
              from investory.account_statistics
              where account_id = -950001
              """));

      assertEquals(
          true,
          exists(
              statement,
              """
              select 1
              from investory.reporting_account_daily_cashflow_reconciliation
              where account_id = -950001
                and snapshot_date = date '2025-01-15'
                and ledger_cash_native is null
              """));
    }
  }

  @Test
  void reconcilesResultOnlyAndSameDayRoundTripContracts() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios(id, name, base_currency)
          values (-200, 'Reconciliation Test', 'USD');

          insert into investory.accounts(id, currency, provider, name, owner, portfolio_id)
          values (-200, 'USD', 'XTB', 'Reconciliation Test', 'Test', -200);

          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values
              (-200, 'Test CFD', 'TESTCFD', 'TESTCFD', 'TESTCFD', 'TESTCFD', 'US', 'USD', 'DERIVATIVE'),
              (-201, 'Test Cash Asset', 'TESTCASH.US', 'TESTCASH', 'TESTCASH', 'TESTCASH', 'US', 'USD', 'EQUITY');

          insert into investory.account_daily(
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity,
              realized_profit, daily_profit_amount)
          values
              (-200, date '2026-01-01', 'USD', 0, 0, 0, 0, 0),
              (-200, date '2026-01-02', 'USD', 50, 0, 50, 52, 50),
              (-200, date '2026-01-03', 'USD', 50, 100, 150, 0, 0),
              (-200, date '2026-01-04', 'USD', -850, 1000, 150, 0, 0);

          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, quality_score, quality_class)
          values
              (-201, date '2026-01-03', 'TEST', 'TESTCASH.US', 'MARKET_CLOSE', 'USD',
               100, 100, 'EXACT_LISTING_MARKET_CLOSE'),
              (-201, date '2026-01-04', 'TEST', 'TESTCASH.US', 'MARKET_CLOSE', 'USD',
               100, 100, 'EXACT_LISTING_MARKET_CLOSE');

          insert into investory.positions(
              id, account_id, asset_id, source_asset_symbol, operation, settlement_model, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, close_time, close_price, margin, commission, swap, profit)
          values
              (-200, -200, -200, 'TESTCFD', 'BUY', 'RESULT_ONLY', 10,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-01 10:00:00+00', 100,
               timestamptz '2026-01-02 10:00:00+00', 105, 100, 0, -2, 50),
              (-201, -200, -201, 'TESTCASH.US', 'BUY', 'CASH_SETTLED', 1,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-01 10:00:00+00', 100,
               timestamptz '2026-01-04 10:00:00+00', 100, null, 0, 0, 0),
              (-202, -200, -201, 'TESTCASH.US', 'BUY', 'CASH_SETTLED', 9,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-04 09:00:00+00', 100,
               timestamptz '2026-01-04 11:00:00+00', 100, null, 0, 0, 0),
              (-203, -200, -201, 'TESTCASH.US', 'BUY', 'CASH_SETTLED', 10,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-04 12:00:00+00', 100,
               null, null, null, 0, 0, 0);

          update investory.positions
          set purchase_value = 100, sale_value = 100
          where id = -201;
          update investory.positions
          set purchase_value = 900, sale_value = 900
          where id = -202;
          update investory.positions
          set purchase_value = 1000
          where id = -203;

          insert into investory.cash_operations(
              id, account_id, operation, asset_id, source_asset_symbol, amount, currency, date)
          values
              (-200, -200, 'CLOSE_TRADE', -200, 'TESTCFD', 52, 'USD',
               timestamptz '2026-01-02 10:00:01+00'),
              (-201, -200, 'SWAP', -200, 'TESTCFD', -2, 'USD',
               timestamptz '2026-01-02 10:00:01+00'),
              (-202, -200, 'STOCK_SELL', -201, 'TESTCASH.US', 1000, 'USD',
               timestamptz '2026-01-04 11:00:01+00'),
              (-203, -200, 'STOCK_PURCHASE', -201, 'TESTCASH.US', -1900, 'USD',
               timestamptz '2026-01-04 12:00:01+00');

          refresh materialized view investory.reporting_trade_settlement_reconciliation;
          """);

      assertEquals(
          "PASS|OK|null|52.00000000|0.00000000",
          singleString(
              statement,
              """
              select reconciliation_status || '|' || anomaly_code || '|'
                  || coalesce(position_close_notional_base::text, 'null') || '|'
                  || position_close_result_base::text || '|'
                  || result_settlement_difference_base::text
              from investory.reporting_trade_settlement_reconciliation
              where account_id = -200 and asset_id = -200
              """));

      assertEquals(
          "PASS|OK|1.00000000|9.00000000|0.00000000|100.0000000000000000|900.0000000000000000|0.00000000000000000000000000000000",
          singleString(
              statement,
              """
              select reconciliation_status || '|' || anomaly_code || '|'
                  || carried_close_quantity::text || '|'
                  || same_day_round_trip_quantity::text || '|'
                  || unmatched_close_quantity::text || '|'
                  || carried_sale_cash_base::text || '|'
                  || same_day_sale_cash_base::text || '|'
                  || symbol_market_bridge_difference_base::text
              from investory.reporting_trade_settlement_reconciliation
              where account_id = -200 and asset_id = -201
              """));
    }
  }

  @Test
  void positionReportingConvertsProfitAndCommissionCurrenciesBeforeSumming() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          delete from investory.exchange_rates;
          insert into investory.portfolios (id, name, base_currency, owner)
          values (-96, 'Mixed currency test', 'USD', 'Test');
          insert into investory.accounts (id, currency, provider, name, owner, portfolio_id, cash_only)
          values
            (-960000, 'PLN', 'XTB', 'Mixed currency PLN account', 'Test', -96, false),
            (-960004, 'USD', 'XTB', 'Mixed currency USD account', 'Test', -96, false);
          insert into investory.exchange_rates (month, source, base, to_currency, rate)
          values
            ((date_trunc('month', current_date) - interval '1 month')::date, 'TEST', 'PLN', 'USD', 0.25),
            ((date_trunc('month', current_date) - interval '1 month')::date, 'TEST', 'EUR', 'USD', 1.25);

          insert into investory.positions (
            id, account_id, asset_id, source_asset_symbol, operation, volume,
            price_currency, cost_currency, profit_currency, commission_currency,
            open_time, open_price, close_time, close_price, purchase_value, sale_value,
            commission, swap, profit
          ) values
            (-960001, -960000, (select id from investory.assets where symbol = 'AAPL.US'),
             'AAPL.US', 'BUY', 1, 'USD', 'USD', 'PLN', 'EUR',
             now() - interval '2 days', 100, now() - interval '1 day', 110, 100, 110,
             -8, 0, 400),
            (-960002, -960000, (select id from investory.assets where symbol = 'AAPL.US'),
             'AAPL.US', 'BUY', 1, 'USD', 'USD', 'USD', 'USD',
             now() - interval '2 days', 100, now() - interval '1 day', 110, 100, 110,
             -2, 0, 50),
            (-960003, -960000, (select id from investory.assets where symbol = 'AAPL.US'),
             'AAPL.US', 'BUY', 1, 'USD', 'USD', 'PLN', 'EUR',
             now() - interval '1 day', 100, null, null, 100, null,
             -4, 0, 200),
            (-960004, -960004, (select id from investory.assets where symbol = 'AAPL.US'),
             'AAPL.US', 'BUY', 2, 'USD', 'USD', 'USD', 'USD',
             now() - interval '1 day', 100, null, null, 200, null,
             0, 0, 0);

          select investory.refresh_reporting_views();
          """);

      assertEquals(
          "138.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(realized_profit::numeric, 8), 'FM9999999990.00000000'))
              from investory.account_statistics
              where account_id = -960000
              """));

      assertEquals(
          "138.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(sum(amount_in_base_currency)::numeric, 8), 'FM9999999990.00000000'))
              from investory.portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'REALIZED'
              """));

      assertEquals(
          "400.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(amount_local::numeric, 8), 'FM9999999990.00000000'))
              from investory.portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'REALIZED' and currency = 'PLN'
              """));
      assertEquals(
          "-8.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(amount_local::numeric, 8), 'FM9999999990.00000000'))
              from investory.portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'REALIZED' and currency = 'EUR'
              """));
      assertEquals(
          "45.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(sum(amount_in_base_currency)::numeric, 8), 'FM9999999990.00000000'))
              from investory.portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'UNREALIZED'
              """));

      assertEquals(
          "300.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(cost_basis_in_base_currency::numeric, 8), 'FM9999999990.00000000'))
              from investory.portfolio_asset_allocation
              where portfolio_id = -96 and asset_symbol = 'AAPL.US'
              """));
    }
  }

  private static boolean viewExists(Statement statement, String viewName) throws Exception {
    return exists(
        statement,
        """
        SELECT 1
        FROM information_schema.views
        WHERE table_schema = 'investory'
          AND table_name = '%s'
        """
            .formatted(viewName));
  }

  private static boolean materializedViewExists(Statement statement, String viewName)
      throws Exception {
    return exists(
        statement,
        """
        SELECT 1
        FROM pg_matviews
        WHERE schemaname = 'investory'
          AND matviewname = '%s'
        """
            .formatted(viewName));
  }

  private static boolean relationColumnExists(
      Statement statement, String relationName, String columnName) throws Exception {
    return exists(
        statement,
        """
        SELECT 1
        FROM pg_attribute attribute
        JOIN pg_class relation ON relation.oid = attribute.attrelid
        JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
        WHERE namespace.nspname = 'investory'
          AND relation.relname = '%s'
          AND attribute.attname = '%s'
          AND attribute.attnum > 0
          AND NOT attribute.attisdropped
        """
            .formatted(relationName, columnName));
  }

  private static boolean columnExists(Statement statement, String relationName, String columnName)
      throws Exception {
    return exists(
        statement,
        """
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'investory'
          AND table_name = '%s'
          AND column_name = '%s'
        """
            .formatted(relationName, columnName));
  }

  private static boolean exists(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      return resultSet.next();
    }
  }

  private static int singleInt(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  private static String singleString(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }
}
