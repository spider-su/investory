package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.SharedPostgres;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorReportingFacts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Migration chain test. It always runs against a disposable PostgreSQL container, never against a
 * developer database, so the destructive schema reset below cannot touch real data.
 */
@DisplayName("Schema Migration Checkpoint2")
class SchemaMigrationCheckpoint2IT {

  private final WorkerDatabase postgres = SharedPostgres.database("migration_checkpoint");

  private String dbUrl() {
    return postgres.jdbcUrl();
  }

  private String dbUsername() {
    return postgres.username();
  }

  private String dbPassword() {
    return postgres.password();
  }

  private Connection openConnection() throws Exception {
    return DriverManager.getConnection(dbUrl(), dbUsername(), dbPassword());
  }

  private Flyway flyway(String target) {
    var configuration =
        Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dbUrl(), dbUsername(), dbPassword())
            .schemas("investory")
            .defaultSchema("investory")
            .locations("classpath:sql/migration");
    if (target != null) configuration.target(target);
    return configuration.load();
  }

  /**
   * Hard safety guard: refuse any destructive statement unless the target is the throwaway
   * container database created for this test class.
   */
  private void assertDisposableTestDatabase() {
    String url = dbUrl();
    if (url == null
        || !url.matches("^jdbc:postgresql://.*/" + postgres.databaseName() + "(?:\\?.*)?$")) {
      throw new IllegalStateException(
          "Refusing to run migration test against non-disposable database: " + url);
    }
  }

  @AfterAll
  static void cleanupDatabase() {
    SharedPostgres.database("migration_checkpoint").close();
  }

  @BeforeEach
  void recreateDatabaseFromEmptySchema() throws Exception {
    assertDisposableTestDatabase();

    Flyway flyway =
        Flyway.configure()
            .cleanDisabled(false)
            .dataSource(dbUrl(), dbUsername(), dbPassword())
            .schemas("investory")
            .defaultSchema("investory")
            .locations("classpath:sql/migration")
            .load();
    flyway.clean();
    flyway.migrate();
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.app_users (id, username, display_name) VALUES "
              + "(2, 'migration-test', 'Migration Test') ON CONFLICT (id) DO NOTHING");
    }
  }

  @DisplayName("applies All Migrations And Creates Checkpoint2Invariants")
  @Test
  void appliesAllMigrationsAndCreatesCheckpoint2Invariants() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          migrationScriptCount(),
          singleInt(
              statement,
              """
              SELECT count(*)
              FROM investory.flyway_schema_history
              WHERE success = true
                AND version IS NOT NULL
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
                AND t.relname = 'import_history'
                AND c.conname = 'chk_import_history_file_sha256_lower_hex'
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
                AND t.relname = 'import_history'
                AND c.conname = 'chk_import_history_rows_balance'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_indexes
              WHERE schemaname = 'investory'
                AND indexname = 'ix_import_history_status_finished_at'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_indexes
              WHERE schemaname = 'investory'
                AND indexname = 'ix_cash_operations_account_date'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_indexes
              WHERE schemaname = 'investory'
                AND indexname = 'ix_positions_account_close_time'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'investory'
                AND table_name = 'asset_types'
              """));
      assertFalse(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.tables
              WHERE table_schema = 'investory'
                AND table_name = 'long_term_asset_cash_flows'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM pg_trigger trigger_row
              JOIN pg_class table_row ON table_row.oid = trigger_row.tgrelid
              JOIN pg_namespace namespace_row ON namespace_row.oid = table_row.relnamespace
              WHERE namespace_row.nspname = 'investory'
                AND table_row.relname = 'long_term_asset_rental_contracts'
                AND trigger_row.tgname = 'longterm_trg_rental_contract_type'
              """));
      assertTrue(
          singleString(
                  statement,
                  "SELECT pg_get_functiondef('investory.longterm_fn_assert_subtype_consistency()'::regprocedure)")
              .contains("long_term_asset_rental_contracts"));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1 FROM pg_trigger
              WHERE tgrelid = 'investory.long_term_asset_real_estate_details'::regclass
                AND tgname = 'longterm_trg_real_estate_details_type'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1 FROM pg_trigger
              WHERE tgrelid = 'investory.long_term_assets'::regclass
                AND tgname = 'longterm_trg_asset_type_consistency'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1 FROM information_schema.columns
              WHERE table_schema = 'investory'
                AND table_name = 'simulation_plans'
                AND column_name = 'current_revision_id'
                AND is_nullable = 'YES'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1 FROM pg_constraint
              WHERE conrelid = 'investory.planning_years'::regclass
                AND pg_get_constraintdef(oid) LIKE 'FOREIGN KEY (portfolio_id)%'
              """));
      assertEquals(
          11,
          singleInt(
              statement,
              """
              SELECT count(*)
              FROM investory.asset_types
              """));
      assertFalse(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'investory'
                AND table_name = 'simulation_plans'
                AND column_name = 'funding_order'
              """));
      assertTrue(
          exists(
              statement,
              """
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema = 'investory'
                AND table_name = 'simulation_plan_revisions'
                AND column_name = 'funding_order'
                AND column_default LIKE '%RESERVE,LONG_TERM,INVESTMENT%'
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
      assertTrue(viewExists(statement, "app_v_normalized_daily_price"));
      assertTrue(viewExists(statement, "app_v_canonical_asset_daily_return"));
      assertTrue(viewExists(statement, "app_v_reconstructed_position_daily"));
      assertTrue(materializedViewExists(statement, "recon_v_reconstructed_position_daily_mv"));
      assertTrue(
          materializedViewExists(statement, "recon_v_reconstructed_account_market_daily_mv"));
      assertTrue(materializedViewExists(statement, "recon_v_reconstructed_cash_daily_mv"));
      assertTrue(viewExists(statement, "recon_v_position_valuation_validation"));
      assertTrue(viewExists(statement, "recon_v_asset_identity_issues"));
      assertTrue(viewExists(statement, "recon_v_asset_price_quality_issues"));
      assertTrue(viewExists(statement, "recon_v_account_daily"));
      assertTrue(materializedViewExists(statement, "recon_v_account_daily_reconciliation_mv"));
      assertTrue(viewExists(statement, "recon_v_non_usd_closed_trade"));
      assertTrue(materializedViewExists(statement, "recon_v_trade_settlement"));
      assertTrue(materializedViewExists(statement, "recon_v_account_monthly_profit"));
      assertTrue(materializedViewExists(statement, "recon_v_account_statistics_vs_daily"));
      assertTrue(materializedViewExists(statement, "recon_v_account_daily_cashflow"));
      assertTrue(viewExists(statement, "recon_v_account_daily_cashflow_full_precision"));
      assertTrue(viewExists(statement, "recon_v_trade_settlement_by_account"));
      assertTrue(viewExists(statement, "recon_v_reporting_validation_summary"));
      assertTrue(viewExists(statement, "recon_v_position_currency_validation"));
      assertTrue(columnExists(statement, "app_v_normalized_daily_price", "selection_priority"));
      assertTrue(columnExists(statement, "app_v_normalized_daily_price", "selected_price_date"));
      assertTrue(
          columnExists(statement, "app_v_normalized_daily_price", "underlying_observation_date"));
      assertTrue(columnExists(statement, "app_v_normalized_daily_price", "price_age_days"));
      assertTrue(
          columnExists(statement, "app_v_reconstructed_position_daily", "contract_multiplier"));
      assertTrue(
          columnExists(statement, "app_v_reconstructed_position_daily", "selected_price_date"));
      assertTrue(
          relationColumnExists(statement, "recon_v_account_daily", "market_value_difference"));
      assertTrue(columnExists(statement, "recon_v_non_usd_closed_trade", "anomaly_code"));
      assertTrue(relationColumnExists(statement, "recon_v_trade_settlement", "settlement_model"));
      assertTrue(relationColumnExists(statement, "recon_v_trade_settlement", "missing_fx_count"));
      assertTrue(relationColumnExists(statement, "recon_v_trade_settlement", "is_complete"));
      assertTrue(relationColumnExists(statement, "recon_v_trade_settlement", "anomaly_code"));
      assertTrue(
          relationColumnExists(
              statement, "recon_v_trade_settlement", "position_close_result_base"));
      assertTrue(
          relationColumnExists(statement, "recon_v_trade_settlement", "carried_close_quantity"));
      assertTrue(
          relationColumnExists(
              statement, "recon_v_trade_settlement", "same_day_round_trip_quantity"));
      assertTrue(
          relationColumnExists(
              statement, "recon_v_trade_settlement", "result_settlement_difference_base"));
      assertTrue(columnExists(statement, "positions", "settlement_model"));
      assertTrue(columnExists(statement, "positions", "broker_product"));
      assertTrue(columnExists(statement, "positions", "source_position_id"));
      assertTrue(columnExists(statement, "positions", "source_row_occurrence"));
      assertTrue(columnExists(statement, "positions", "source_open_price"));
      assertTrue(columnExists(statement, "positions", "source_close_price"));
      assertTrue(columnExists(statement, "positions", "open_conversion_rate"));
      assertTrue(columnExists(statement, "positions", "close_conversion_rate"));
      assertTrue(columnExists(statement, "recon_v_reporting_validation_summary", "status"));
      assertTrue(columnExists(statement, "app_v_open_position_values", "account_currency"));
      assertTrue(columnExists(statement, "app_v_open_position_values", "position_row_count"));
      assertTrue(columnExists(statement, "recon_v_position_currency_validation", "anomaly_code"));
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
                  where a.symbol = 'JGPI.DE'
                    and aph.price_origin = 'MANUAL_WEEKLY'
                  """)
              > 0);
      assertEquals(
          "Apple Inc.",
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

  @DisplayName("rental contract trigger accepts real estate and rejects every other asset type")
  @Test
  void rentalContractTriggerEnforcesRealEstateSubtype() throws Exception {
    assertSubtypeRejected(9101, "BOND");
    assertSubtypeRejected(9102, "DEPOSIT");
    assertSubtypeRejected(9103, "CASH_RESERVE");
    assertSubtypeRejected(9104, "OTHER");

    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO investory.long_term_assets
              (id, portfolio_id, name, asset_type, currency, current_value)
          VALUES (9105, 1, 'Allowed rental', 'REAL_ESTATE', 'PLN', 100000);
          INSERT INTO investory.long_term_asset_rental_contracts
              (id, asset_id, start_date, end_date)
          VALUES (9105, 9105, DATE '2026-01-01', DATE '2026-12-31');
          """);
      assertEquals(
          1,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.long_term_asset_rental_contracts WHERE id = 9105"));
    }
  }

  @DisplayName("historical inverse FX interpolation is linear in the directed rate")
  @Test
  void historicalInverseFxInterpolationUsesDirectedRate() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "DELETE FROM investory.exchange_rates WHERE source = 'TEST' AND rate_date BETWEEN DATE '2000-01-01' AND DATE '2000-01-03'");
      statement.executeUpdate(
          """
          INSERT INTO investory.exchange_rates(rate_date, base, to_currency, rate, source, method)
          VALUES (DATE '2000-01-01', 'USD', 'EUR', 2, 'TEST', 'HISTORICAL_MONTHLY'),
                 (DATE '2000-01-03', 'USD', 'EUR', 4, 'TEST', 'HISTORICAL_MONTHLY')
          """);
      assertEquals(
          3.0,
          Double.parseDouble(
              singleString(
                  statement,
                  "SELECT fx_rate_to_target FROM investory.resolve_fx_rate(DATE '2000-01-02', 'USD', 'EUR')")),
          0.000001);
      assertEquals(
          0.375,
          Double.parseDouble(
              singleString(
                  statement,
                  "SELECT fx_rate_to_target FROM investory.resolve_fx_rate(DATE '2000-01-02', 'EUR', 'USD')")),
          0.000001);
    }
  }

  private void assertSubtypeRejected(long assetId, String assetType) throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO investory.long_term_assets "
              + "(id, portfolio_id, name, asset_type, currency, current_value) VALUES ("
              + assetId
              + ", 1, 'Invalid rental', '"
              + assetType
              + "', 'PLN', 100000)");
      assertThrows(
          SQLException.class,
          () ->
              statement.execute(
                  "INSERT INTO investory.long_term_asset_rental_contracts "
                      + "(asset_id, start_date, end_date) VALUES ("
                      + assetId
                      + ", DATE '2026-01-01', DATE '2026-12-31')"));
    }
  }

  @DisplayName("monthly Profit Boundary Uses Canonical Account Flow Scope")
  @Test
  void monthlyProfitBoundaryUsesCanonicalAccountFlowScope() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT definition FROM pg_matviews "
                    + "WHERE schemaname = 'investory' "
                    + "AND matviewname = 'recon_v_account_monthly_profit'")) {
      assertTrue(result.next(), "monthly reconciliation materialized view must exist");
      String definition = result.getString(1).toLowerCase(java.util.Locale.ROOT);
      assertTrue(definition.contains("normalized_cash_operations"));
      assertTrue(definition.contains("is_external_flow"));
      assertTrue(definition.contains("is_internal_transfer"));
    }
  }

  @DisplayName("classifies Ibkr Cash Transfer With Enriched Comment As External Deposit")
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
      statement.execute("SELECT investory.refresh_app_views()");
      assertEquals(
          "EXTERNAL_DEPOSIT",
          singleString(
              statement,
              """
              select normalized_category
              from investory.app_v_normalized_cash_operations
              where operation_id = -900001
              """));
    }
  }

  @DisplayName("stores Jgpi With Declared Currency Across Asset History")
  @Test
  void storesJgpiWithDeclaredCurrencyAcrossAssetHistory() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          "EUR",
          singleString(
              statement,
              """
              select currency
              from investory.assets
              where symbol = 'JGPI.DE'
              """));
      assertEquals(
          "EUR",
          singleString(
              statement,
              """
              select price_currency
              from investory.asset_price_history
              where asset_id = (select id from investory.assets where symbol = 'JGPI.DE')
                and source = 'MANUAL'
                and source_symbol = 'jgpi.de'
                and price_date = date '2025-01-01'
              """));
    }
  }

  @DisplayName("preserves Vhyl Distributing Identity Across Mappings")
  @Test
  @Disabled
  void preservesVhylDistributingIdentityAcrossMappings() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          "VHYL.UK|VHYL|VHYL|VHYL.L|Vanguard FTSE All-World High Dividend Yield UCITS ETF (USD) Distributing",
          singleString(
              statement,
              "select symbol || '|' || ticker || '|' || ibkr || '|' || yahoo || '|' || name "
                  + "from investory.assets where ibkr = 'VHYL'"));
      assertEquals(
          "VHYL.UK",
          singleString(
              statement,
              "select a.symbol from investory.asset_source_symbols ass "
                  + "join investory.assets a on a.id = ass.asset_id "
                  + "where ass.source = 'STOOQ' and lower(ass.source_symbol) = 'vhyl.uk'"));
      assertEquals(
          0,
          singleInt(statement, "select count(*) from investory.assets where symbol = 'VHYA.UK'"));
    }
  }

  @DisplayName("validates Included Asset Identity And Mapping Contracts")
  @Test
  void validatesIncludedAssetIdentityAndMappingContracts() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) from investory.recon_v_asset_identity_issues "
                  + "where severity = 'ERROR'"));
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) from investory.recon_v_asset_identity_issues i "
                  + "join investory.assets a on a.id = i.asset_id "
                  + "where a.exclude_from_import"));
      assertTrue(
          exists(
              statement, "select 1 from pg_constraint where conname = 'chk_assets_isin_format'"));
      assertTrue(
          exists(
              statement,
              "select 1 from pg_constraint where conname = 'chk_assets_exchange_mic_format'"));
    }
  }

  @DisplayName("flags Included Price Quality Anomalies Without Rejecting History")
  @Test
  void flagsIncludedPriceQualityAnomaliesWithoutRejectingHistory() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, quality_score, quality_class, is_observed, is_proxy, price_scale_factor)
          values
              ((select id from investory.assets where symbol = 'JGPI.DE'), date '2026-05-01',
               'QUALITY_A', 'JGPI.DE', 'MANUAL', 'EUR', 100, 80, 'MANUAL', true, false, 1),
              ((select id from investory.assets where symbol = 'JGPI.DE'), date '2026-05-01',
               'QUALITY_B', 'JGPI.DE', 'MANUAL', 'EUR', 200, 80, 'MANUAL', true, false, 1),
              ((select id from investory.assets where symbol = 'JGPI.DE'), date '2026-05-02',
               'QUALITY_C', 'JGPI.DE', 'MANUAL', 'EUR', 30000, 80, 'MANUAL', true, false, 1),
              ((select id from investory.assets where symbol = 'JGPI.DE'), date '2026-06-01',
               'QUALITY_D', 'JGPI.DE', 'MANUAL', 'EUR', 100, 80, 'MANUAL', true, false, 1),
              ((select id from investory.assets where symbol = 'JGPI.DE'), date '2026-06-03',
               'QUALITY_E', 'JGPI.DE', 'MANUAL', 'EUR', 100, 80, 'MANUAL', true, false, 1)
          """);
      statement.execute(
          """
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, estimated, interpolation_method, interpolation_left_date,
              interpolation_right_date, quality_score, quality_class, is_observed,
              is_proxy, price_scale_factor)
          values ((select id from investory.assets where symbol = 'JGPI.DE'), date '2026-06-02',
              'QUALITY_F', 'JGPI.DE', 'INTERPOLATED_XTB', 'EUR', 500, true,
              'LINEAR_BUSINESS_DAY', date '2026-06-01', date '2026-06-03', 80,
              'INTERPOLATED_XTB', false, false, 1)
          """);
      statement.execute("select investory.refresh_app_views()");
      assertTrue(
          singleInt(
                  statement,
                  "select count(*) from investory.recon_v_asset_price_quality_issues "
                      + "where issue_code = 'EXTREME_SAME_DATE_SOURCE_DISAGREEMENT' "
                      + "and asset_symbol = 'JGPI.DE' and price_date = date '2026-05-01'")
              > 0);
      assertTrue(
          singleInt(
                  statement,
                  "select count(*) from investory.recon_v_asset_price_quality_issues "
                      + "where issue_code = 'EXTREME_DAILY_MOVE' "
                      + "and asset_symbol = 'JGPI.DE' and price_date = date '2026-05-02'")
              > 0);
      assertTrue(
          singleInt(
                  statement,
                  "select count(*) from investory.recon_v_asset_price_quality_issues "
                      + "where issue_code = 'SUSPICIOUS_INTERPOLATION' "
                      + "and asset_symbol = 'JGPI.DE' and price_date = date '2026-06-02'")
              > 0);
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) from investory.recon_v_asset_price_quality_issues i "
                  + "join investory.assets a on a.id = i.asset_id "
                  + "where a.exclude_from_import"));
    }
  }

  @DisplayName("recomputes Included Derived Views And Reports Reconciliation Summary")
  @Test
  void recomputesIncludedDerivedViewsAndReportsReconciliationSummary() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("select investory.refresh_app_views()");
      statement.execute("select investory.refresh_reconstructed_position_daily()");
      statement.execute("select investory.refresh_reconstructed_account_market_daily()");
      statement.execute("select investory.refresh_reconstructed_cash_daily()");
      statement.execute("select investory.refresh_account_daily_reconciliation()");
      statement.execute("select investory.refresh_reconciliation_reporting_views()");

      int marketValueMismatches =
          singleInt(
              statement,
              "select count(*) from investory.recon_v_account_daily "
                  + "where status = 'FAIL' and validation_message = 'market value mismatch'");
      int costBasisMismatches =
          singleInt(
              statement,
              "select count(*) from investory.recon_v_account_daily "
                  + "where status = 'FAIL' and validation_message = 'cost base mismatch'");
      int missingPrices =
          singleInt(
              statement,
              "select count(*) from investory.recon_v_position_valuation_validation "
                  + "where validation_code = 'MISSING_PRICE'");
      int missingMultipliers =
          singleInt(
              statement,
              "select count(*) from investory.recon_v_position_valuation_validation "
                  + "where validation_code = 'MISSING_MULTIPLIER'");
      int currencyInconsistencies =
          singleInt(
              statement,
              "select count(*) from investory.recon_v_asset_price_quality_issues "
                  + "where issue_code = 'PRICE_CURRENCY_MISMATCH'");
      String largestDifference =
          singleString(
              statement,
              "select to_char(coalesce(max(greatest("
                  + "abs(coalesce(market_value_difference, 0)), "
                  + "abs(coalesce(cost_base_difference, 0)), "
                  + "abs(coalesce(unrealized_difference, 0)), "
                  + "abs(coalesce(realized_difference, 0)))), 0), 'FM9999999990.00000000') "
                  + "from investory.recon_v_account_daily");
      String summary =
          "market_value_mismatches="
              + marketValueMismatches
              + ",cost_basis_mismatches="
              + costBasisMismatches
              + ",missing_prices="
              + missingPrices
              + ",missing_multipliers="
              + missingMultipliers
              + ",currency_inconsistencies="
              + currencyInconsistencies
              + ",largest_difference="
              + largestDifference;
      System.out.println("INCLUDED_RECONCILIATION_SUMMARY " + summary);
      assertEquals(0, missingMultipliers);
      assertEquals(0, marketValueMismatches, summary);
      assertEquals(0, costBasisMismatches, summary);
      assertEquals(0, missingPrices, summary);
      assertEquals(0, currencyInconsistencies, summary);
    }
  }

  @DisplayName("excluded Assets Are Absent From Valuation And Price Diagnostics")
  @Test
  void excludedAssetsAreAbsentFromValuationAndPriceDiagnostics() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      long excludedAssetId =
          singleLong(
              statement,
              "SELECT id FROM investory.assets WHERE exclude_from_import ORDER BY id LIMIT 1");
      String excludedCurrency =
          singleString(
              statement, "SELECT currency FROM investory.assets WHERE id = " + excludedAssetId);
      statement.execute(
          """
          insert into investory.cash_operations
              (id, account_id, operation, asset_id, amount, currency, comment, date)
          values
              (-910001, 17959259, 'DIVIDEND', %d, 12.00, '%s', 'excluded dividend', timestamptz '2026-01-02 00:00:00+01:00'),
              (-910002, 17959259, 'COMMISSION', %d, -1.00, '%s', 'excluded fee', timestamptz '2026-01-02 00:00:01+01:00'),
              (-910003, 17959259, 'STOCK_PURCHASE', %d, -100.00, '%s', 'excluded trade-linked cash', timestamptz '2026-01-02 00:00:02+01:00'),
              (-910004, 17959259, 'DEPOSIT', null, 50.00, 'USD', 'account-level deposit', timestamptz '2026-01-02 00:00:03+01:00')
          """
              .formatted(
                  excludedAssetId,
                  excludedCurrency,
                  excludedAssetId,
                  excludedCurrency,
                  excludedAssetId,
                  excludedCurrency));
      statement.execute("SELECT investory.refresh_app_views()");
      assertEquals(
          4,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.cash_operations WHERE id BETWEEN -910004 AND -910001"));
      assertEquals(
          1,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.app_v_normalized_cash_operations WHERE operation_id BETWEEN -910004 AND -910001"));
      assertEquals(
          1,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.app_v_normalized_cash_operations WHERE operation_id = -910004"));
      assertEquals(
          0,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.app_v_canonical_asset_daily_price cp "
                  + "JOIN investory.assets a ON a.id = cp.asset_id "
                  + "WHERE a.exclude_from_import"));
      assertEquals(
          0,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_price_history_contract_issues i "
                  + "JOIN investory.assets a ON a.id = i.asset_id "
                  + "WHERE a.exclude_from_import"));
      assertEquals(
          0,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.app_v_current_asset_price "
                  + "WHERE asset_id IN (SELECT id FROM investory.assets WHERE exclude_from_import)"));
    }
  }

  @DisplayName("price History Contract Diagnostics Are Empty After Seed Repair")
  @Test
  void priceHistoryContractDiagnosticsAreEmptyAfterSeedRepair() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          0,
          singleInt(
              statement, "SELECT count(*) FROM investory.recon_v_price_history_contract_issues"));
      assertEquals(
          0,
          singleInt(
              statement,
              "SELECT count(*) FROM investory.asset_price_history aph "
                  + "JOIN investory.assets a ON a.id = aph.asset_id "
                  + "WHERE a.symbol IN ('NUCL.UK', 'JGPI.DE', 'HPRD.UK', 'VHYA.UK', 'SPYW.DE') "
                  + "AND aph.price_currency IS DISTINCT FROM a.currency"));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
              SELECT price_currency
              FROM investory.asset_price_history
              WHERE asset_id = (SELECT id FROM investory.assets WHERE symbol = 'EMIM.UK')
                AND source = 'STOOQ'
                AND source_symbol = 'emim.uk'
              ORDER BY price_date DESC
              LIMIT 1
              """));
    }
  }

  @DisplayName("canonical Price Selection Is Unique And Deterministic Across Sources")
  @Test
  void canonicalPriceSelectionIsUniqueAndDeterministicAcrossSources() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, quality_score, quality_class, is_observed, is_proxy, price_scale_factor)
          values (
              (select id from investory.assets where symbol = 'JGPI.DE'), date '2025-01-01',
              'CANONICAL_TEST', 'JGPI.DE', 'MANUAL', 'EUR', 99.00000000, 99,
              'MANUAL', true, false, 1.00000000)
          """);
      statement.execute("select investory.refresh_app_views();");
      assertTrue(
          singleInt(
                  statement,
                  "select count(*) from investory.asset_price_history "
                      + "where asset_id = (select id from investory.assets where symbol = 'JGPI.DE') "
                      + "and price_date = date '2025-01-01'")
              > 1);
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) from ("
                  + "select asset_id, price_date from investory.app_v_canonical_asset_daily_price "
                  + "group by asset_id, price_date having count(*) > 1"
                  + ") duplicate_canonical_rows"));
      assertEquals(
          "CANONICAL_TEST",
          singleString(
              statement,
              "select source from investory.app_v_canonical_asset_daily_price "
                  + "where asset_id = (select id from investory.assets where symbol = 'JGPI.DE') "
                  + "and price_date = date '2025-01-01'"));
    }
  }

  @DisplayName("corporate Action Uses Adjusted Return Basis And Compatible Position Quantity")
  @Test
  void corporateActionUsesAdjustedReturnBasisAndCompatiblePositionQuantity() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios(id, name, base_currency, user_id)
          values (-470000, 'Corporate action test', 'USD', 1);
          insert into investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-470000, '470000', 'USD', 'XTB', 'Corporate action test', 'Test', -470000);
          insert into investory.account_daily(
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity)
          values
              (-470000, date '2026-02-10', 'USD', 0, 100, 100),
              (-470000, date '2026-02-11', 'USD', 0, 100, 100);
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, adjusted_close_price, quality_score, quality_class,
              is_observed, is_proxy, price_scale_factor)
          values
              ((select id from investory.assets where symbol = 'PALL.US'), date '2026-02-10',
               'CORPORATE_TEST', 'PALL.US', 'MANUAL', 'USD', 100, 10, 99,
               'EXACT_LISTING_MARKET_CLOSE', true, false, 1),
              ((select id from investory.assets where symbol = 'PALL.US'), date '2026-02-11',
               'CORPORATE_TEST', 'PALL.US', 'MANUAL', 'USD', 10, 10, 99,
               'EXACT_LISTING_MARKET_CLOSE', true, false, 1);
          insert into investory.positions(
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, purchase_value)
          values
              (-470001, -470000, (select id from investory.assets where symbol = 'PALL.US'),
               'PALL.US', 'BUY', 1, 'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-02-10 10:00:00+00', 100, 100),
              (-470002, -470000, (select id from investory.assets where symbol = 'PALL.US'),
               'PALL.US', 'BUY', 9, 'USD', 'USD', 'USD', 'USD',
              timestamptz '2026-02-11 10:00:00+00', 0, 0);
           select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_app_views();
           select investory.refresh_reconciliation_reporting_views();
          """);
      assertEquals(
          "100.00000000|100.00000000",
          singleString(
              statement,
              "select string_agg(trim(to_char(reconstructed_market_value_base, 'FM9999999990.00000000')), '|' "
                  + "order by valuation_date) "
                  + "from investory.app_v_reconstructed_position_daily "
                  + "where account_id = -470000 and asset_id = "
                  + "(select id from investory.assets where symbol = 'PALL.US') "
                  + "and valuation_date in (date '2026-02-10', date '2026-02-11')"));
      assertEquals(
          "0.00000000",
          singleString(
              statement,
              "select trim(to_char(daily_return_pct, 'FM9999999990.00000000')) "
                  + "from investory.app_v_canonical_asset_daily_return "
                  + "where asset_id = (select id from investory.assets where symbol = 'PALL.US') "
                  + "and price_date = date '2026-02-11'"));
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) "
                  + "from investory.recon_v_position_valuation_validation "
                  + "where account_id = -470000 and asset_id = (select id from investory.assets where symbol = 'PALL.US') "
                  + "and valuation_date = date '2026-02-11'"));
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) from investory.app_v_canonical_asset_daily_return r "
                  + "join investory.assets a on a.id = r.asset_id "
                  + "where a.exclude_from_import"));
    }
  }

  @DisplayName("bond Monetary Price Uses Unit Contract Multiplier")
  @Test
  void bondMonetaryPriceUsesUnitContractMultiplier() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios(id, name, base_currency, user_id)
          values (-458022, 'Bond multiplier test', 'USD', 1);
          insert into investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-458022, '458022', 'USD', 'IBKR', 'Bond multiplier test', 'Test', -458022);
          insert into investory.account_daily(
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity,
              realized_profit, daily_profit_amount)
          values (-458022, date '2026-01-15', 'USD', 0, 1000, 1000, 0, 0);
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, quality_score, quality_class)
          values (
              (select id from investory.assets where symbol = 'US91282CKB62'),
              date '2026-01-15', 'TEST', 'US91282CKB62', 'MARKET_CLOSE', 'USD',
              1000, 100, 'EXACT_LISTING_MARKET_CLOSE');
          insert into investory.positions(
              id, account_id, asset_id, source_asset_symbol, operation, settlement_model, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, purchase_value)
          values (
              -458022, -458022,
              (select id from investory.assets where symbol = 'US91282CKB62'),
              'US91282CKB62', 'BUY', 'CASH_SETTLED', 1,
              'USD', 'USD', 'USD', 'USD',
              timestamptz '2026-01-15 09:00:00+00', 1000, 1000);
           select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_app_views();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "1.00000000|1000.00000000",
          singleString(
              statement,
              "select trim(to_char(contract_multiplier, 'FM9999999990.00000000')) || '|' "
                  + "|| trim(to_char(reconstructed_market_value_base, 'FM9999999990.00000000')) "
                  + "from investory.app_v_reconstructed_position_daily "
                  + "where account_id = -458022 and valuation_date = date '2026-01-15'"));
    }
  }

  @DisplayName("current Position Cost Basis Uses Current Valuation Date Fx")
  @Test
  void currentPositionCostBasisUsesCurrentValuationDateFx() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios(id, name, base_currency, user_id)
          values (-440000, 'Cost basis FX test', 'USD', 1);
          insert into investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-440000, '440000', 'USD', 'IBKR', 'Cost basis FX test', 'Test', -440000);
          insert into investory.account_daily(
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity)
          values (-440000, current_date - 1, 'USD', 0, 0, 0);
          insert into investory.exchange_rates(
              rate_date, base, to_currency, rate, source, method)
          values
              (current_date - 1, 'EUR', 'USD', 1.10, 'TEST', 'MARKET_DAILY'),
              (current_date, 'EUR', 'USD', 2.00, 'TEST', 'MARKET_DAILY');
          insert into investory.positions(
              id, account_id, asset_id, source_asset_symbol, operation, settlement_model, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, purchase_value)
          values (
              -440000, -440000,
              (select id from investory.assets where symbol = 'JGPI.DE'),
              'JGPI.DE', 'BUY', 'CASH_SETTLED', 1,
              'EUR', 'EUR', 'EUR', 'EUR',
              (current_date - 1)::timestamptz, 1000, 1000);
          select investory.refresh_app_views();
          """);

      assertEquals(
          "2000.00000000|2000.00000000",
          singleString(
              statement,
              "select trim(to_char(cost_basis_in_base_currency, 'FM9999999990.00000000')) || '|' "
                  + "|| trim(to_char(cost_basis_in_base_currency, 'FM9999999990.00000000')) "
                  + "from investory.app_v_current_open_position_rows "
                  + "where account_id = -440000 and asset_id = "
                  + "(select id from investory.assets where symbol = 'JGPI.DE')"));
    }
  }

  @DisplayName("advances Portfolio Sequence Past Seeded Ids")
  @Test
  void advancesPortfolioSequencePastSeededIds() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          2,
          singleInt(
              statement,
              """
              insert into investory.portfolios (name, base_currency, user_id)
              values ('Sequence regression portfolio', 'USD', 1)
              returning id
              """));
    }
  }

  @DisplayName("converts Account Daily Values To Portfolio Base Currency")
  @Test
  void convertsAccountDailyValuesToPortfolioBaseCurrency() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-920001, 'EUR reporting portfolio', 'EUR', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-920001, '920001', 'USD', 'IBKR', 'USD account', 'Test', -920001);
          insert into investory.exchange_rates (
              rate_date, base, to_currency, rate, source, method
          ) values (
              date '2025-01-15', 'USD', 'EUR', 0.96000000, 'TEST', 'MARKET_DAILY'
          );
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values (-920001, date '2025-01-15', 'USD', 100, 50, 150)
          """);

      assertEquals(
          "144.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(equity::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_daily
              where portfolio_id = -920001
              """));
    }
  }

  @DisplayName("requires Core Raw Row Fields")
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

  @DisplayName("same Currency Position Does Not Report Missing Fx")
  @Test
  void sameCurrencyPositionDoesNotReportMissingFx() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-930001, 'USD FX regression portfolio', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-930001, '930001', 'USD', 'IBKR', 'USD account', 'Test', -930001);
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, profit
          ) values (
              -930001, -930001, (select id from investory.assets where symbol = 'AAPL.US'),
              'AAPL', 'BUY', 1, 'USD', 'USD', 'USD', 'USD',
              timestamptz '2025-01-01 00:00:00+00', 100, 10
          );
          select investory.refresh_app_views()
          """);
      statement.execute("select investory.refresh_app_views();");

      assertEquals(
          0,
          singleInt(
              statement,
              """
              select fallback_position_fx_missing_count
              from investory.recon_v_portfolio_service_fallback
              where portfolio_id = -930001
              """));
    }
  }

  @DisplayName("keeps Usd Cost Currency For Usd Asset Held In Pln Accounts")
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

      assertEquals(
          0,
          singleInt(
              statement,
              """
          select count(*)
          from investory.recon_v_position_currency_validation
          where asset_id = (select id from investory.assets where symbol = 'MSFT.US')
            and anomaly_code = 'POSITION_ASSET_CURRENCY_MISMATCH'
          """));

      assertEquals(
          "USD",
          singleString(
              statement,
              """
              select cost_currency
              from investory.positions
              where id = -910002
              """));
      assertEquals(
          "USD",
          singleString(
              statement,
              """
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
              from investory.app_v_open_position_values
              where asset_symbol = 'MSFT.US'
                and account_id = 50290466
              """));

      assertEquals(
          "5754.42000000",
          singleString(
              statement,
              """
              select trim(to_char(round(sum(cost_basis_in_base_currency)::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_open_position_values
              where asset_symbol = 'MSFT.US'
              """));
    }
  }

  @DisplayName(
      "canonical Portfolio Fx Supports Same Currency Direct Inverse Triangulation Stale And Missing")
  @Test
  void canonicalPortfolioFxSupportsSameCurrencyDirectInverseTriangulationStaleAndMissing()
      throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          delete from investory.exchange_rates;

          insert into investory.exchange_rates (rate_date, source, base, to_currency, rate, method)
          values
            (date '2025-01-01', 'TEST', 'USD', 'EUR', 0.8, 'MARKET_DAILY'),
            (date '2025-01-01', 'TEST', 'USD', 'PLN', 4.0, 'MARKET_DAILY');

          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-940001, 'USD FX test portfolio', 'USD', 1),
                 (-940002, 'EUR FX test portfolio', 'EUR', 1);

          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-940001, '940001', 'USD', 'IBKR', 'USD FX account', 'Test', -940001),
                 (-940002, '940002', 'EUR', 'IBKR', 'EUR FX account', 'Test', -940002);

          insert into investory.account_daily (
            account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values
            (-940001, date '2025-01-15', 'USD', 0, 0, 0),
            (-940001, date '2025-04-20', 'USD', 0, 0, 0),
            (-940001, date '2024-01-10', 'USD', 0, 0, 0),
            (-940002, date '2025-01-15', 'EUR', 0, 0, 0);
          """);

      statement.execute("select investory.refresh_app_views()");
      assertEquals(
          "SAME_CURRENCY",
          singleString(
              statement,
              """
              select conversion_status
              from investory.app_v_portfolio_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2025-01-15'
                and source_currency = 'USD'
              """));

      assertEquals(
          "1.25000000",
          singleString(
              statement,
              """
              select trim(to_char(round(fx_rate_to_base::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2025-01-15'
                and source_currency = 'EUR'
              """));

      assertEquals(
          "0.80000000",
          singleString(
              statement,
              """
              select trim(to_char(round(fx_rate_to_base::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_daily_fx_rate
              where portfolio_id = -940002
                and valuation_date = date '2025-01-15'
                and source_currency = 'USD'
              """));

      assertEquals(
          "0.20000000",
          singleString(
              statement,
              """
              select trim(to_char(round(fx_rate_to_base::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_daily_fx_rate
              where portfolio_id = -940002
                and valuation_date = date '2025-01-15'
                and source_currency = 'PLN'
              """));

      assertEquals(
          "STALE",
          singleString(
              statement,
              """
              select conversion_status
              from investory.app_v_portfolio_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2025-04-20'
                and source_currency = 'EUR'
              """));

      assertEquals(
          "MISSING_RATE",
          singleString(
              statement,
              """
              select conversion_status
              from investory.app_v_portfolio_daily_fx_rate
              where portfolio_id = -940001
                and valuation_date = date '2024-01-10'
                and source_currency = 'EUR'
              """));
    }
  }

  @DisplayName("account Statistics Fail Closed When One Cash Operation Has Missing Fx")
  @Test
  void accountStatisticsFailClosedWhenOneCashOperationHasMissingFx() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          delete from investory.exchange_rates;
          insert into investory.exchange_rates (rate_date, source, base, to_currency, rate)
          values (date '2025-01-15', 'TEST', 'USD', 'EUR', 0.8);

          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-950001, 'Missing FX portfolio', 'USD', 1);

          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-950001, '950001', 'USD', 'IBKR', 'Missing FX account', 'Test', -950001);

          insert into investory.cash_operations (id, account_id, operation, asset_id, amount, currency, comment, date)
          values
            (-950101, -950001, 'DEPOSIT', null, 100.00, 'USD', 'usd deposit', timestamptz '2025-01-15 00:00:00+00'),
            (-950102, -950001, 'DEPOSIT', null, 80.00, 'EUR', 'eur deposit', timestamptz '2025-01-15 00:00:00+00'),
            (-950103, -950001, 'DEPOSIT', null, 400.00, 'PLN', 'pln deposit missing fx', timestamptz '2025-01-15 00:00:00+00');

          insert into investory.account_daily (
            account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values (-950001, date '2025-01-15', 'USD', 0, 0, 0);

          select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_app_views();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          1,
          singleInt(
              statement,
              """
              select count(*)
              from investory.app_v_normalized_cash_operations
              where account_id = -950001
                and portfolio_conversion_status = 'MISSING_RATE'
              """));

      assertEquals(
          1,
          singleInt(
              statement,
              """
              select missing_fx_count
              from investory.app_v_account_statistics
              where account_id = -950001
              """));

      assertTrue(
          exists(
              statement,
              """
                        select 1
                        from investory.app_v_account_statistics
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
              from investory.app_v_account_statistics
              where account_id = -950001
              """));

      assertTrue(
          exists(
              statement,
              """
                        select 1
                        from investory.recon_v_account_daily_cashflow
                        where account_id = -950001
                          and snapshot_date = date '2025-01-15'
                          and ledger_cash_native is null
                        """));
    }
  }

  @DisplayName("reconciles Result Only And Same Day Round Trip Contracts")
  @Test
  void reconcilesResultOnlyAndSameDayRoundTripContracts() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios(id, name, base_currency, user_id)
          values (-200, 'Reconciliation Test', 'USD', 1);

          insert into investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-200, '200', 'USD', 'XTB', 'Reconciliation Test', 'Test', -200);

          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values
              (-200, 'Test CFD', 'TESTCFD', 'TESTCFD', 'TESTCFD', 'TESTCFD', 'US', 'USD', 'DERIVATIVE'),
              (-201, 'Test Cash AssetEntity', 'TESTCASH.US', 'TESTCASH', 'TESTCASH', 'TESTCASH', 'US', 'USD', 'EQUITY');

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
              open_time, open_price, close_time, close_price, purchase_value, sale_value,
              commission, swap, profit
          ) values
              (-200, -200, -200, 'TESTCFD', 'BUY', 'RESULT_ONLY', 10,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-01 10:00:00+00', 100,
               timestamptz '2026-01-02 10:00:00+00', 105, 100, 0, -2, 0, 50),
              (-201, -200, -201, 'TESTCASH.US', 'BUY', 'CASH_SETTLED', 1,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-01 10:00:00+00', 100,
               timestamptz '2026-01-04 10:00:00+00', 100, null, 0, 0, 0, 0),
              (-202, -200, -201, 'TESTCASH.US', 'BUY', 'CASH_SETTLED', 9,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-04 09:00:00+00', 100,
               timestamptz '2026-01-04 11:00:00+00', 100, null, 0, 0, 0, 0),
              (-203, -200, -201, 'TESTCASH.US', 'BUY', 'CASH_SETTLED', 10,
               'USD', 'USD', 'USD', 'USD',
               timestamptz '2026-01-04 12:00:00+00', 100,
               null, null, null, 0, 0, 0, 0);

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

          """);

      statement.execute("SELECT investory.refresh_app_views()");
      statement.execute("SELECT investory.refresh_reconstructed_position_daily()");
      statement.execute("SELECT investory.refresh_reconstructed_account_market_daily()");
      statement.execute("SELECT investory.refresh_reconstructed_cash_daily()");
      statement.execute("SELECT investory.refresh_account_daily_reconciliation()");
      statement.execute("SELECT investory.refresh_reconciliation_reporting_views()");

      assertEquals(
          "PASS|OK|null|52.00000000|0.00",
          singleString(
              statement,
              """
              select reconciliation_status || '|' || anomaly_code || '|'
                  || coalesce(position_close_notional_base::text, 'null') || '|'
                  || position_close_result_base::text || '|'
                  || trim(to_char(result_settlement_difference_base,
                                  'FM9999999990.00'))
              from investory.recon_v_trade_settlement
              where account_id = -200 and asset_id = -200
              """));

      assertEquals(
          "PASS|OK|1.00000000|9.00000000|0.00000000|100.0000000000000000|900.0000000000000000|0",
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
              from investory.recon_v_trade_settlement
              where account_id = -200 and asset_id = -201
              """));

      assertEquals(
          "10.00000000",
          singleString(
              statement,
              "select trim(to_char(open_quantity, 'FM9999999990.00000000')) "
                  + "from investory.app_v_reconstructed_position_daily "
                  + "where account_id = -200 and asset_id = -201 "
                  + "and valuation_date = date '2026-01-04'"));
      assertEquals(
          0,
          singleInt(
              statement,
              "select count(*) from investory.app_v_reconstructed_position_daily "
                  + "where account_id = -200 and asset_id = -200 "
                  + "and valuation_date = date '2026-01-02'"));
      assertEquals(
          1,
          singleInt(
              statement,
              "select count(*) from investory.app_v_reconstructed_position_daily "
                  + "where account_id = -200 and asset_id = -201 "
                  + "and valuation_date = date '2026-01-03'"));
      assertEquals(
          1,
          singleInt(
              statement,
              "select count(*) from investory.app_v_reconstructed_position_daily "
                  + "where account_id = -200 and asset_id = -201 "
                  + "and valuation_date = date '2026-01-01'"));
    }
  }

  @DisplayName("historical Excluded Asset Still Resolves Currency In Position Validation")
  @Test
  void historicalExcludedAssetStillResolvesCurrencyInPositionValidation() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          update investory.assets
             set active = false, exclude_from_import = true
           where symbol = 'MSFT.US'
          """);
      statement.execute(
          """
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, purchase_value
          ) values (
              -910101, 17959259, (select id from investory.assets where symbol = 'MSFT.US'), 'MSFT',
              'BUY', 1, 'USD', 'USD', 'USD', 'USD',
              timestamptz '2025-07-01 12:00:00+00', 400, 400
          )
          """);

      assertEquals(
          "USD",
          singleString(
              statement, "select currency from investory.assets where symbol = 'MSFT.US'"));
      assertEquals(
          0,
          singleInt(
              statement,
              """
              select count(*)
              from investory.recon_v_position_currency_validation
              where position_id = -910101
                and anomaly_code = 'MISSING_ASSET_CURRENCY'
              """));
    }
  }

  @DisplayName("account Statistics Uses Cash Ledger Result For Usd Result Only Positions")
  @Test
  void accountStatisticsUsesCashLedgerResultForUsdResultOnlyPositions() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios(id, name, base_currency, user_id)
          values (-973, 'Result-only accounting test', 'USD', 1);

          insert into investory.accounts(id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-973001, '973001', 'USD', 'XTB', 'Result-only USD', 'Test', -973);

          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values (-973001, 'Test CFD', 'RESULTONLYCFD', 'RESULTONLYCFD', 'RESULTONLYCFD',
                  'RESULTONLYCFD', 'US', 'USD', 'DERIVATIVE');

          insert into investory.positions(
              id, account_id, asset_id, source_asset_symbol, operation, settlement_model, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, close_time, close_price, purchase_value, sale_value,
              commission, swap, profit)
          values (-973001, -973001, -973001, 'RESULTONLYCFD', 'BUY', 'RESULT_ONLY', 1,
                  'USD', 'USD', 'USD', 'USD',
                  timestamptz '2026-01-01 10:00:00+00', 100,
                  timestamptz '2026-01-02 10:00:00+00', 0, 0, 0, 0, -2, 50);

          insert into investory.cash_operations(
              id, account_id, operation, asset_id, source_asset_symbol, amount, currency, date)
          values
              (-973001, -973001, 'CLOSE_TRADE', -973001, 'RESULTONLYCFD', 52, 'USD',
               timestamptz '2026-01-02 10:00:01+00'),
              (-973002, -973001, 'SWAP', -973001, 'RESULTONLYCFD', -2, 'USD',
               timestamptz '2026-01-02 10:00:01+00');

          insert into investory.account_daily(
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity,
              realized_profit, fees, daily_profit_amount)
          values (-973001, date '2026-01-02', 'USD', 50, 0, 50, 50, 2, 50);

          select investory.refresh_app_views();
          select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "50.00000000|50.00000000|0.00000000",
          singleString(
              statement,
              """
              select trim(to_char(statistics_realized_profit, 'FM9999999990.00000000')) || '|'
                  || trim(to_char(cumulative_daily_realized_profit, 'FM9999999990.00000000')) || '|'
                  || trim(to_char(realized_profit_difference, 'FM9999999990.00000000'))
              from investory.recon_v_account_statistics_vs_daily
              where account_id = -973001
              """));
    }
  }

  @DisplayName("position Reporting Converts Profit And Commission Currencies Before Summing")
  @Test
  void positionReportingConvertsProfitAndCommissionCurrenciesBeforeSumming() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          delete from investory.exchange_rates;
          insert into investory.portfolios (id, name, base_currency, owner, user_id)
          values (-96, 'Mixed currency test', 'USD', 'Test', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id, cash_only)
          values
            (-960000, '960000', 'PLN', 'XTB', 'Mixed currency PLN account', 'Test', -96, false),
            (-960004, '960004', 'USD', 'XTB', 'Mixed currency USD account', 'Test', -96, false);
          insert into investory.exchange_rates (rate_date, source, base, to_currency, rate)
          values
            (current_date - interval '1 day', 'TEST', 'PLN', 'USD', 0.25),
            (current_date - interval '1 day', 'TEST', 'EUR', 'USD', 1.25);

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

          update investory.assets
             set market_price = 150,
                 market_price_usd = 150,
                 price_source = 'TEST',
                 price_updated_at = now()
           where symbol = 'AAPL.US';

          select investory.refresh_app_views();
          """);

      assertEquals(
          HappyInvestorReportingFacts.MIXED_CURRENCY_REALIZED_RESULT.toPlainString(),
          singleString(
              statement,
              """
              select trim(to_char(round(realized_profit::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_account_statistics
              where account_id = -960000
              """));

      assertEquals(
          HappyInvestorReportingFacts.MIXED_CURRENCY_REALIZED_RESULT.toPlainString(),
          singleString(
              statement,
              """
              select trim(to_char(round(sum(amount_in_base_currency)::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'REALIZED'
              """));

      assertEquals(
          HappyInvestorReportingFacts.MIXED_CURRENCY_PLN_COMPONENT.toPlainString(),
          singleString(
              statement,
              """
              select trim(to_char(round(amount_local::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'REALIZED' and currency = 'PLN'
              """));
      assertEquals(
          HappyInvestorReportingFacts.MIXED_CURRENCY_EUR_FEE.toPlainString(),
          singleString(
              statement,
              """
              select trim(to_char(round(amount_local::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'REALIZED' and currency = 'EUR'
              """));
      assertEquals(
          "45.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(sum(amount_in_base_currency)::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_currency_breakdown
              where portfolio_id = -96 and metric_type = 'UNREALIZED'
              """));

      assertEquals(
          "300.00000000",
          singleString(
              statement,
              """
              select trim(to_char(round(cost_basis_in_base_currency::numeric, 8), 'FM9999999990.00000000'))
              from investory.app_v_portfolio_asset_allocation
              where portfolio_id = -96 and asset_symbol = 'AAPL.US'
              """));
    }
  }

  @DisplayName("normalized Daily Price Prefers Fresh Effective Observation And Rejects Future Data")
  @Test
  void normalizedDailyPricePrefersFreshEffectiveObservationAndRejectsFutureData() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-970001, 'Price precedence test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-970001, '970001', 'USD', 'IBKR', 'Price precedence account', 'Test', -970001);
          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values
              (-970001, 'Price precedence asset', 'PRICE_PRECEDENCE_TEST.US',
               'PRICE_PRECEDENCE_TEST', 'PRICE_PRECEDENCE_TEST', 'PRICE_PRECEDENCE_TEST',
               'US', 'USD', 'EQUITY'),
              (-970002, 'Carry-forward asset', 'CARRY_FORWARD_TEST.US',
               'CARRY_FORWARD_TEST', 'CARRY_FORWARD_TEST', 'CARRY_FORWARD_TEST',
               'US', 'USD', 'EQUITY');
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price
          ) values
              (-970001, -970001, -970001, 'PRICE_PRECEDENCE_TEST.US', 'BUY', 1,
               'USD', 'USD', 'USD', 'USD', timestamptz '2029-12-01 00:00:00+00', 100),
              (-970002, -970001, -970002, 'CARRY_FORWARD_TEST.US', 'BUY', 1,
               'USD', 'USD', 'USD', 'USD', timestamptz '2029-12-01 00:00:00+00', 100);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values (-970001, date '2030-01-10', 'USD', 0, 0, 0);
          insert into investory.asset_price_history (
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, source_date, quality_score, quality_class
          ) values
              (-970001, date '2030-01-02', 'TEST_EXACT', 'PRICE_PRECEDENCE_TEST.US',
               'MARKET_CLOSE', 'USD', 506.70, date '2030-01-02', 100,
               'EXACT_LISTING_MARKET_CLOSE'),
              (-970001, date '2030-01-08', 'TEST_TRADE', 'PRICE_PRECEDENCE_TEST.US',
               'TRADE_OBSERVATION', 'USD', 401.95, date '2030-01-08', 10,
               'TRADE_OBSERVATION'),
              (-970001, date '2030-01-08', 'TEST_TRADE_ALT', 'PRICE_PRECEDENCE_TEST.US',
               'TRADE_OBSERVATION', 'USD', 402.95, date '2030-01-08', 20,
               'TRADE_OBSERVATION'),
              (-970001, date '2030-01-11', 'TEST_FUTURE', 'PRICE_PRECEDENCE_TEST.US',
               'MARKET_CLOSE', 'USD', 999.99, date '2030-01-11', 100,
               'EXACT_LISTING_MARKET_CLOSE'),
              (-970002, date '2030-01-09', 'TEST_CARRY', 'CARRY_FORWARD_TEST.US',
               'STALE_CARRY_FORWARD', 'USD', 399.00, date '2030-01-08', 10,
               'STALE_CARRY_FORWARD');
          """);

      statement.execute("select investory.refresh_app_views()");
      assertEquals(
          "402.95000000|2030-01-08|2030-01-08|2",
          singleString(
              statement,
              """
              select trim(to_char(selected_price, 'FM9999999990.00000000')) || '|'
                  || selected_price_date::text || '|'
                  || underlying_observation_date::text || '|'
                  || price_age_days::text
              from investory.app_v_normalized_daily_price
              where asset_id = -970001
                and valuation_date = date '2030-01-10'
              """));

      assertEquals(
          "399.00000000|2030-01-09|2030-01-08|2",
          singleString(
              statement,
              """
              select trim(to_char(selected_price, 'FM9999999990.00000000')) || '|'
                  || selected_price_date::text || '|'
                  || underlying_observation_date::text || '|'
                  || price_age_days::text
              from investory.app_v_normalized_daily_price
              where asset_id = -970002
                and valuation_date = date '2030-01-10'
              """));
    }
  }

  @DisplayName("position Valuation Does Not Bridge AClosed Holding Gap")
  @Test
  void positionValuationDoesNotBridgeAClosedHoldingGap() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-972001, 'Trade continuity test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-972001, '972001', 'USD', 'IBKR', 'Trade continuity account', 'Test', -972001);
          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values (-972001, 'Trade continuity asset', 'TRADE_CONTINUITY.US',
                  'TRADE_CONTINUITY', 'TRADE_CONTINUITY', 'TRADE_CONTINUITY', 'US', 'USD', 'EQUITY');
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, close_time
          ) values
              (-972001, -972001, -972001, 'TRADE_CONTINUITY.US', 'BUY', 1,
               'USD', 'USD', 'USD', 'USD', timestamptz '2030-01-01 00:00:00+00', 100,
               timestamptz '2030-01-02 00:00:00+00'),
              (-972002, -972001, -972001, 'TRADE_CONTINUITY.US', 'BUY', 1,
               'USD', 'USD', 'USD', 'USD', timestamptz '2030-01-05 00:00:00+00', 10, NULL);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values
              (-972001, date '2030-01-01', 'USD', 0, 0, 0),
              (-972001, date '2030-01-05', 'USD', 0, 0, 0);
          insert into investory.asset_price_history (
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, source_date, quality_score, quality_class
          ) values
              (-972001, date '2030-01-01', 'TEST_MARKET', 'TRADE_CONTINUITY.US',
               'MARKET_CLOSE', 'USD', 100, date '2030-01-01', 100,
               'EXACT_LISTING_MARKET_CLOSE'),
              (-972001, date '2030-01-05', 'TEST_TRADE', 'TRADE_CONTINUITY.US',
               'TRADE_OBSERVATION', 'USD', 10, date '2030-01-05', 90,
               'TRADE_OBSERVATION');
           select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_app_views();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "TRADE_OBSERVATION_SELECTED|NULL",
          singleString(
              statement,
              """
              select validation_code || '|' || coalesce(expected_value::text, 'NULL')
              from investory.recon_v_position_valuation_validation
              where account_id = -972001
                and asset_id = -972001
                and valuation_date = date '2030-01-05'
              """));
    }
  }

  @DisplayName("approved Alternate Listing Does Not Invent Previous Price Expected Value")
  @Test
  void approvedAlternateListingDoesNotInventPreviousPriceExpectedValue() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-973001, 'Alternate listing test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-973001, '973001', 'USD', 'IBKR', 'Alternate listing account', 'Test', -973001);
          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values (-973001, 'Alternate listing asset', 'ALT_TEST.UK',
                  'ALT_TEST', 'ALT_TEST', 'ALT_TEST', 'UK', 'USD', 'EQUITY');
          insert into investory.asset_source_symbols(
              asset_id, source, source_symbol, price_currency,
              is_alternate_listing, match_method, match_status, manual_approval_status)
          values (-973001, 'STOOQ', 'alt_test.uk', 'USD', true,
                  'MANUAL_ALTERNATE_LISTING', 'ACCEPTED_ALTERNATE_LISTING', 'APPROVED_IN_GENERATOR');
          insert into investory.positions(
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price)
          values (-973001, -973001, -973001, 'ALT_TEST.UK', 'BUY', 1,
                  'USD', 'USD', 'USD', 'USD', timestamptz '2030-01-01 00:00:00+00', 200);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity)
          values
              (-973001, date '2030-01-01', 'USD', 0, 200, 200),
              (-973001, date '2030-01-02', 'USD', 0, 250, 250);
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, quality_score, quality_class, is_observed, is_proxy)
          values
              (-973001, date '2030-01-01', 'STOOQ', 'alt_test.uk', 'STOOQ', 'USD',
               200, 90, 'VERIFIED_ALTERNATE_LISTING', true, true),
              (-973001, date '2030-01-02', 'STOOQ', 'alt_test.uk', 'STOOQ', 'USD',
               250, 90, 'VERIFIED_ALTERNATE_LISTING', true, true);
           select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "ALTERNATE_LISTING_SELECTED|NULL",
          singleString(
              statement,
              "select validation_code || '|' || coalesce(expected_value::text, 'NULL') "
                  + "from investory.recon_v_position_valuation_validation "
                  + "where account_id = -973001 and asset_id = -973001 "
                  + "and valuation_date = date '2030-01-02'"));
    }
  }

  @DisplayName("manual Weekly Price Movement Is An Explicit Review Not An Ok Mismatch")
  @Test
  void manualWeeklyPriceMovementIsAnExplicitReviewNotAnOkMismatch() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-974001, 'Manual weekly test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-974001, '974001', 'USD', 'IBKR', 'Manual weekly account', 'Test', -974001);
          insert into investory.assets(
              id, name, symbol, ticker, ibkr, yahoo, country, currency, asset_type)
          values (-974001, 'Manual weekly asset', 'MANUAL_WEEKLY_TEST.US',
                  'MANUAL_WEEKLY_TEST', 'MANUAL_WEEKLY_TEST', 'MANUAL_WEEKLY_TEST', 'US', 'USD', 'EQUITY');
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price
          ) values (-974001, -974001, -974001, 'MANUAL_WEEKLY_TEST.US', 'BUY', 1,
                    'USD', 'USD', 'USD', 'USD', timestamptz '2030-01-01 00:00:00+00', 100);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values
              (-974001, date '2030-01-01', 'USD', 0, 100, 100),
              (-974001, date '2030-01-02', 'USD', 0, 95, 95),
              (-974001, date '2030-01-03', 'USD', 0, 103, 103),
              (-974001, date '2030-01-04', 'USD', 0, 104, 104),
              (-974001, date '2030-01-05', 'USD', 0, 102, 102);
          insert into investory.asset_price_history(
              asset_id, price_date, source, source_symbol, price_origin, price_currency,
              close_price, source_date, quality_score, quality_class, is_observed
          ) values
              (-974001, date '2030-01-01', 'TEST_STALE', 'MANUAL_WEEKLY_TEST.US',
               'STALE_CARRY_FORWARD', 'USD', 100, date '2030-01-01', 40,
               'STALE_CARRY_FORWARD', true),
              (-974001, date '2030-01-02', 'TEST_MANUAL', 'MANUAL_WEEKLY_TEST.US',
               'MANUAL_WEEKLY', 'USD', 95, date '2030-01-02', 90,
               'MANUAL_WEEKLY_CLOSE', true),
              (-974001, date '2030-01-03', 'TEST_MANUAL', 'MANUAL_WEEKLY_TEST.US',
               'MANUAL_WEEKLY', 'USD', 103, date '2030-01-03', 90,
               'MANUAL_WEEKLY_CLOSE', true),
              (-974001, date '2030-01-04', 'TEST_TRADE', 'MANUAL_WEEKLY_TEST.US',
               'IBKR_TRADE', 'USD', 104, date '2030-01-04', 80,
               'IBKR_TRADE_OBSERVATION', true),
              (-974001, date '2030-01-05', 'TEST_MANUAL', 'MANUAL_WEEKLY_TEST.US',
               'MANUAL_WEEKLY', 'USD', 102, date '2030-01-05', 90,
               'MANUAL_WEEKLY_CLOSE', true);
           select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "MANUAL_WEEKLY_CONTINUITY_REVIEW|NULL|95.00000000",
          singleString(
              statement,
              "select validation_code || '|' || coalesce(expected_value::text, 'NULL') || '|' "
                  + "|| trim(to_char((select selected_price from investory.app_v_normalized_daily_price "
                  + "where asset_id = -974001 and valuation_date = date '2030-01-02'), 'FM9999999990.00000000')) "
                  + "from investory.recon_v_position_valuation_validation "
                  + "where account_id = -974001 and asset_id = -974001 "
                  + "and valuation_date = date '2030-01-02'"));
      assertEquals(
          "MANUAL_WEEKLY_CONTINUITY_REVIEW|NULL|103.00000000",
          singleString(
              statement,
              "select validation_code || '|' || coalesce(expected_value::text, 'NULL') || '|' "
                  + "|| trim(to_char((select selected_price from investory.app_v_normalized_daily_price "
                  + "where asset_id = -974001 and valuation_date = date '2030-01-03'), 'FM9999999990.00000000')) "
                  + "from investory.recon_v_position_valuation_validation "
                  + "where account_id = -974001 and asset_id = -974001 "
                  + "and valuation_date = date '2030-01-03'"));
      assertEquals(
          "MANUAL_WEEKLY_CONTINUITY_REVIEW|NULL|102.00000000",
          singleString(
              statement,
              "select validation_code || '|' || coalesce(expected_value::text, 'NULL') || '|' "
                  + "|| trim(to_char((select selected_price from investory.app_v_normalized_daily_price "
                  + "where asset_id = -974001 and valuation_date = date '2030-01-05'), 'FM9999999990.00000000')) "
                  + "from investory.recon_v_position_valuation_validation "
                  + "where account_id = -974001 and asset_id = -974001 "
                  + "and valuation_date = date '2030-01-05'"));
    }
  }

  @DisplayName("account Daily Reconciliation Uses Total Signed Realized Result")
  @Test
  void accountDailyReconciliationUsesTotalSignedRealizedResult() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-971001, 'Realized result test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-971001, '971001', 'USD', 'IBKR', 'Realized result account', 'Test', -971001);
          insert into investory.positions (
              id, account_id, asset_id, source_asset_symbol, operation, volume,
              price_currency, cost_currency, profit_currency, commission_currency,
              open_time, open_price, close_time, close_price, purchase_value, sale_value,
              commission, swap, profit
          ) values (
              -971001, -971001, (select id from investory.assets where symbol = 'AAPL.US'),
              'AAPL.US', 'BUY', 1, 'USD', 'USD', 'USD', 'USD',
              timestamptz '2030-02-01 00:00:00+00', 100,
              timestamptz '2030-02-02 00:00:00+00', 110, 100, 110,
              -2, -10, 100
          );
          insert into investory.cash_operations (
              id, account_id, operation, amount, currency, date, comment
          ) values (
              -971101, -971001, 'SWAP', -10, 'USD',
              timestamptz '2030-02-02 00:00:01+00', 'signed swap cash row'
          );
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value,
              equity, realized_profit
          ) values (-971001, date '2030-02-02', 'USD', -10, 0, -10, 88);
           select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "88.00000000|10.00000000",
          singleString(
              statement,
              """
              select trim(to_char(reconstructed_total_realized_result, 'FM9999999990.00000000')) || '|'
                  || trim(to_char(cash_swap_component, 'FM9999999990.00000000'))
              from investory.recon_v_realized_result
              where account_id = -971001
                and valuation_date = date '2030-02-02'
              """));

      assertEquals(
          "88.00000000|0.00000000|PASS",
          singleString(
              statement,
              """
              select trim(to_char(reconstructed_total_realized_result, 'FM9999999990.00000000')) || '|'
                  || trim(to_char(realized_difference, 'FM9999999990.00000000')) || '|'
                  || status
              from investory.recon_v_account_daily
              where account_id = -971001
                and valuation_date = date '2030-02-02'
              """));
    }
  }

  @DisplayName("zero Realized Difference Does Not Hide Another Reconciliation Failure")
  @Test
  void zeroRealizedDifferenceDoesNotHideAnotherReconciliationFailure() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-971011, 'Aggregate status test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-971011, '971011', 'USD', 'IBKR', 'Aggregate status account', 'Test', -971011);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value,
              equity, cost_base, unrealized_profit, realized_profit
          ) values (-971011, date '2030-02-02', 'USD', 0, 100, 100, 0, 0, 0);
          select investory.refresh_reconstructed_position_daily();
          select investory.refresh_reconstructed_account_market_daily();
          select investory.refresh_reconstructed_cash_daily();
          select investory.refresh_account_daily_reconciliation();
          """);

      assertEquals(
          "0.00000000|FAIL|market value mismatch",
          singleString(
              statement,
              """
              select trim(to_char(realized_difference, 'FM9999999990.00000000')) || '|'
                  || status || '|' || validation_message
              from investory.recon_v_account_daily
              where account_id = -971011
                and valuation_date = date '2030-02-02'
              """));
    }
  }

  @DisplayName("statistics Reconciliation Labels Different As Of Dates Explicitly")
  @Test
  void statisticsReconciliationLabelsDifferentAsOfDatesExplicitly() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-972001, 'Statistics as-of test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-972001, '972001', 'USD', 'IBKR', 'Statistics as-of account', 'Test', -972001);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value, equity
          ) values (-972001, current_date - 1, 'USD', 100, 0, 100);
          select investory.refresh_app_views();
           select investory.refresh_reconstructed_position_daily();
           select investory.refresh_reconstructed_account_market_daily();
           select investory.refresh_reconstructed_cash_daily();
           select investory.refresh_account_daily_reconciliation();
           select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "VALUATION_ASOF_DIFFERENCE",
          singleString(
              statement,
              """
              select reconciliation_status
              from investory.recon_v_account_statistics_vs_daily
              where account_id = -972001
              """));
    }
  }

  @DisplayName("statistics Reconciliation Labels Realized Only Difference As Semantic Review")
  @Test
  void statisticsReconciliationLabelsRealizedOnlyDifferenceAsSemanticReview() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          insert into investory.portfolios (id, name, base_currency, user_id)
          values (-972011, 'Statistics realized semantic test', 'USD', 1);
          insert into investory.accounts (id, external_account_id, currency, provider, name, owner, portfolio_id)
          values (-972011, '972011', 'USD', 'IBKR', 'Statistics realized semantic account', 'Test', -972011);
          insert into investory.account_daily (
              account_id, snapshot_date, valuation_currency, cash_balance, market_value,
              equity, cost_base, unrealized_profit, realized_profit
          ) values (-972011, current_date, 'USD', 100, 0, 100, 0, 0, 10);
          select investory.refresh_app_views();
          select investory.refresh_reconciliation_reporting_views();
          """);

      assertEquals(
          "REALIZED_PROFIT_SEMANTIC_REVIEW|-10.00|0.00|0.00",
          singleString(
              statement,
              """
              select reconciliation_status || '|'
                  || trim(to_char(realized_profit_difference, 'FM9999999990.00')) || '|'
                  || trim(to_char(market_value_difference, 'FM9999999990.00')) || '|'
                  || trim(to_char(equity_difference, 'FM9999999990.00'))
              from investory.recon_v_account_statistics_vs_daily
              where account_id = -972011
              """));
    }
  }

  @DisplayName("reconciliation Tolerance Uses Full Precision And Configured Display Scale")
  @Test
  void reconciliationToleranceUsesFullPrecisionAndConfiguredDisplayScale() throws Exception {
    try (Connection connection = openConnection();
        Statement statement = connection.createStatement()) {
      assertEquals(
          "0|0.05000000|0.00001000",
          singleString(
              statement,
              """
              select investory.reconciliation_parameter('reconciliation_reporting_scale')::integer::text || '|'
                  || trim(to_char(investory.reconciliation_parameter('reconciliation_absolute_tolerance'), 'FM999999990.00000000')) || '|'
                  || trim(to_char(investory.reconciliation_parameter('reconciliation_relative_tolerance'), 'FM999999990.00000000'))
              """));

      assertEquals(
          "0.000001",
          singleString(
              statement,
              "select trim(to_char(investory.reconciliation_parameter('reconciliation_quantity_tolerance'), 'FM999999990.000000'))"));

      assertEquals(
          "true|true|false",
          singleString(
              statement,
              """
              select investory.reconciliation_values_match(100, 100.049)::text || '|'
                  || investory.reconciliation_values_match(100, 100.05)::text || '|'
                  || investory.reconciliation_values_match(100, 100.051)::text
              """));

      assertEquals(
          "true|false",
          singleString(
              statement,
              """
              select investory.reconciliation_values_match(100000, 100000.5)::text || '|'
                  || investory.reconciliation_values_match(100000, 100001.01)::text
              """));

      statement.execute(
          """
          update investory.reconciliation_parameters
             set numeric_value = 0.001
           where parameter_name = 'reconciliation_absolute_tolerance';
          """);

      assertEquals(
          "100.00|100.00|100.004|100.000|false",
          singleString(
              statement,
              """
              select to_char(investory.reconciliation_display_value(100.004), 'FM999999990.00') || '|'
                  || to_char(investory.reconciliation_display_value(100.000), 'FM999999990.00') || '|'
                  || '100.004' || '|100.000|'
                  || investory.reconciliation_values_match(100.004, 100.000)::text
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

  private static long singleLong(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }

  private static String singleString(Statement statement, String sql) throws Exception {
    try (ResultSet resultSet = statement.executeQuery(sql)) {
      resultSet.next();
      return resultSet.getString(1);
    }
  }

  private static int migrationScriptCount() throws Exception {
    try (Stream<Path> files = Files.list(Path.of("src", "main", "resources", "sql", "migration"))) {
      return (int)
          files
              .map(Path::getFileName)
              .map(Path::toString)
              .filter(name -> name.matches("^V\\d+\\.\\d+__.*\\.sql$"))
              .count();
    }
  }
}
