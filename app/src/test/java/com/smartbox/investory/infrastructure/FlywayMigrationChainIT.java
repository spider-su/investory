package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Cheap proof that the complete Flyway chain applies to an empty PostgreSQL database. */
class FlywayMigrationChainIT {

  private static final WorkerDatabase DATABASE =
      MigrationTestDatabase.open("flyway_migration_chain");

  @BeforeAll
  static void migrateEmptyDatabase() {
    MigrationTestDatabase.migrate(DATABASE);
  }

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @Test
  void appliesEveryMigrationSuccessfully() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          MigrationTestDatabase.migrationScriptCount(),
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.flyway_schema_history "
                  + "WHERE success AND version IS NOT NULL"));
    }
  }

  @Test
  void installsReferenceBaselineAndLeavesOperationalWorkspaceEmpty() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.accounting_poc_profile "
                  + "WHERE id = 1 AND has_uop"));
      assertEquals(
          8,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.accounting_reference_month "
                  + "WHERE tax_period >= DATE '2026-01-01' AND tax_period < DATE '2026-09-01'"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement, "SELECT count(*) FROM investory.accounting_poc_invoice"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement, "SELECT count(*) FROM investory.accounting_poc_expense_invoice"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement, "SELECT count(*) FROM investory.accounting_poc_bank_transaction"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement, "SELECT count(*) FROM investory.accounting_tmp_invoice"));
    }
  }

  @Test
  void copiesReferenceObligationsIntoRyczaltOwnedTable() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.accounting_reference_obligation source "
                  + "WHERE NOT EXISTS (SELECT 1 FROM investory.ryczalt_obligation_reference target "
                  + "WHERE target.id = source.id "
                  + "AND target.profile_id = source.profile_id "
                  + "AND target.tax_period = source.tax_period "
                  + "AND target.obligation_type = source.obligation_type "
                  + "AND target.due_date IS NOT DISTINCT FROM source.due_date "
                  + "AND target.paid_amount IS NOT DISTINCT FROM source.paid_amount "
                  + "AND target.payment_date IS NOT DISTINCT FROM source.payment_date "
                  + "AND target.status = source.status "
                  + "AND target.note IS NOT DISTINCT FROM source.note)"));
    }
  }

  @Test
  void installsTemporalAnomalyContractAndParameters() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM pg_views WHERE schemaname = 'investory' "
                  + "AND viewname = 'recon_v_temporal_anomaly'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.reconciliation_parameters "
                  + "WHERE parameter_name = 'reconciliation_account_unexplained_move_ratio'"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly " + "WHERE false"));
    }
  }

  @Test
  void temporalAnomaliesDetectStructuralPatternsWithoutCallingFlowsMarketMovement()
      throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      statement.execute("BEGIN");
      statement.execute(
          "INSERT INTO investory.assets(id,name,symbol,ticker,ibkr,country,currency,asset_type) "
              + "VALUES (88000001,'Temporal Test Asset','TEMP.TEST','TEMP.TEST','TEMP.TEST','US','USD','EQUITY')");
      statement.execute(
          "INSERT INTO investory.asset_source_symbols(asset_id,source,source_symbol,price_currency,active,is_exact_listing,price_scale_factor) "
              + "VALUES (88000001,'TEST','TEMP.TEST','USD',true,true,1)");
      statement.execute(
          "INSERT INTO investory.asset_price_history(asset_id,price_date,source,source_symbol,source_mapping_id,price_origin,price_currency,close_price,quality_class,is_observed,is_proxy,price_scale_factor) "
              + "SELECT 88000001,v.d,'TEST','TEMP.TEST',m.id,'MARKET_DATA',v.currency,v.price,'OBSERVED',true,false,1 "
              + "FROM investory.asset_source_symbols m CROSS JOIN (VALUES "
              + "(DATE '2025-01-10','USD',100::numeric),(DATE '2025-01-11','PLN',10000::numeric),"
              + "(DATE '2025-01-12','USD',102::numeric),(DATE '2025-01-30','USD',204::numeric)) v(d,currency,price) "
              + "WHERE m.asset_id=88000001");
      statement.execute(
          "DELETE FROM investory.exchange_rates WHERE rate_date BETWEEN DATE '2025-01-01' AND DATE '2025-01-04' "
              + "AND ((base = 'USD' AND to_currency = 'PLN') OR (base = 'PLN' AND to_currency = 'USD'))");
      statement.execute(
          "INSERT INTO investory.exchange_rates(rate_date,base,to_currency,rate,source,method,source_rate_date) VALUES "
              + "('2025-01-01','USD','PLN',1,'TEST','OBSERVED','2025-01-01'),"
              + "('2025-01-02','USD','PLN',1.04,'TEST','OBSERVED','2025-01-02'),"
              + "('2025-01-03','USD','PLN',1.50,'TEST','OBSERVED','2025-01-03'),"
              + "('2025-01-04','USD','PLN',1.02,'TEST','OBSERVED','2025-01-04'),"
              + "('2025-01-01','PLN','USD',1,'TEST','OBSERVED','2025-01-01'),"
              + "('2025-01-02','PLN','USD',1/1.04,'TEST','OBSERVED','2025-01-02'),"
              + "('2025-01-03','PLN','USD',.60,'TEST','OBSERVED','2025-01-03'),"
              + "('2025-01-04','PLN','USD',1/1.02,'TEST','OBSERVED','2025-01-04')");
      statement.execute(
          "INSERT INTO investory.accounts(id,external_account_id,currency,provider,name,owner,portfolio_id) "
              + "VALUES (88000001,'TEMP-ACCOUNT','USD','XTB','Temporal Test Account','test',2)");
      statement.execute(
          "INSERT INTO investory.account_daily(account_id,snapshot_date,valuation_currency,equity,market_value,cash_balance,deposits) VALUES "
              + "(88000001,'2025-01-01','USD',100,100,0,0),"
              + "(88000001,'2025-01-02','USD',200,100,100,100),"
              + "(88000001,'2025-01-03','USD',300,200,100,0)");

      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'FX_EXTREME_MOVE' AND entity_key = 'USD/PLN' "
                  + "AND event_date = DATE '2025-01-02'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'FX_ISOLATED_SPIKE' AND entity_key = 'USD/PLN' "
                  + "AND event_date = DATE '2025-01-03'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'PRICE_ISOLATED_SPIKE' AND entity_key = 'TEMP.TEST'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'PRICE_CURRENCY_MISMATCH' AND entity_key = 'TEMP.TEST'"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'PRICE_EXTREME_MOVE' AND event_date = DATE '2025-01-30'"));
      assertEquals(
          0,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'ACCOUNT_MARKET_VALUE_SPIKE' AND event_date = DATE '2025-01-02'"));
      assertEquals(
          1,
          MigrationTestDatabase.singleInt(
              statement,
              "SELECT count(*) FROM investory.recon_v_temporal_anomaly "
                  + "WHERE issue_code = 'ACCOUNT_MARKET_VALUE_SPIKE' AND event_date = DATE '2025-01-03'"));
      statement.execute("ROLLBACK");
    }
  }
}
