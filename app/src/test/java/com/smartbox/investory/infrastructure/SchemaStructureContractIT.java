package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Protects the small set of relations and columns required by application composition. */
class SchemaStructureContractIT {

  private static final WorkerDatabase DATABASE = MigrationTestDatabase.open("schema_structure");

  private static final Set<String> REQUIRED_TABLES =
      Set.of(
          "app_users",
          "portfolios",
          "accounts",
          "currencies",
          "assets",
          "asset_types",
          "cash_operations",
          "positions",
          "asset_price_history",
          "account_daily",
          "bond",
          "real_estate",
          "cash_reserve",
          "personal_asset",
          "rental_contract",
          "rental_contract_term",
          "retirement_plans",
          "retirement_plan_events",
          "retirement_planning_years",
          "accounting_document",
          "accounting_document_vat_bucket");

  private static final Set<String> REMOVED_RETIREMENT_TABLES =
      Set.of(
          "simulation_plans",
          "simulation_plan_revisions",
          "simulation_plan_revision_events",
          "planning_years",
          "planning_year_values");

  private static final Set<String> REQUIRED_RELATIONS =
      Set.of(
          "app_v_normalized_daily_price",
          "app_v_reconstructed_position_daily",
          "app_v_open_position_values",
          "app_v_long_term_assets",
          "recon_v_account_daily",
          "recon_v_trade_settlement",
          "recon_v_reporting_validation_summary",
          "recon_v_position_currency_validation");

  @BeforeAll
  static void migrateEmptyDatabase() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void createsRequiredApplicationSchema() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      for (String table : REQUIRED_TABLES) {
        assertTrue(
            MigrationTestDatabase.exists(
                statement,
                "SELECT 1 FROM information_schema.tables "
                    + "WHERE table_schema = 'investory' AND table_name = '"
                    + table
                    + "'"),
            table);
      }
      for (String relation : REQUIRED_RELATIONS) {
        assertTrue(
            MigrationTestDatabase.exists(
                statement,
                "SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                    + "WHERE n.nspname = 'investory' AND c.relname = '"
                    + relation
                    + "'"),
            relation);
      }
      for (String table : REMOVED_RETIREMENT_TABLES) {
        assertFalse(
            MigrationTestDatabase.exists(
                statement,
                "SELECT 1 FROM information_schema.tables "
                    + "WHERE table_schema = 'investory' AND table_name = '"
                    + table
                    + "'"),
            table);
      }

      assertFalse(
          MigrationTestDatabase.exists(
              statement,
              "SELECT 1 FROM information_schema.tables "
                  + "WHERE table_schema = 'investory' AND table_name = 'deposit'"));
      assertFalse(
          MigrationTestDatabase.exists(
              statement,
              "SELECT 1 FROM information_schema.tables "
                  + "WHERE table_schema = 'investory' AND table_name = 'real_estate_valuation'"));
      assertFalse(
          MigrationTestDatabase.exists(
              statement,
              "SELECT 1 FROM investory.app_v_long_term_assets "
                  + "WHERE asset_type NOT IN ('REAL_ESTATE', 'BOND', 'CASH_RESERVE', 'PERSONAL_ASSET')"));

      assertTrue(
          MigrationTestDatabase.exists(
              statement,
              "SELECT 1 FROM information_schema.columns "
                  + "WHERE table_schema = 'investory' AND table_name = 'positions' "
                  + "AND column_name IN ('settlement_model', 'open_conversion_rate', 'close_conversion_rate')"));
      assertTrue(
          MigrationTestDatabase.exists(
              statement,
              "SELECT 1 FROM information_schema.columns "
                  + "WHERE table_schema = 'investory' AND table_name = 'employment_period' "
                  + "AND column_name = 'qualifies_as_primary_social_insurance'"));
    }
  }
}
