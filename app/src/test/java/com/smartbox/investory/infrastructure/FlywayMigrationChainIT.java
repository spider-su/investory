package com.smartbox.investory.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.testsupport.WorkerDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
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
  void overlappingSourceObservationIsAccountedForByLinkedLogicalIdentity() throws Exception {
    try (Connection connection = MigrationTestDatabase.connection(DATABASE);
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        statement.execute(
            "INSERT INTO investory.app_users(username,display_name) "
                + "VALUES ('c0-overlap-test','C0 overlap test')");
        statement.execute(
            "INSERT INTO investory.portfolios(name,base_currency,user_id) "
                + "SELECT 'C0 overlap test','USD',id FROM investory.app_users "
                + "WHERE username='c0-overlap-test'");
        statement.execute(
            "INSERT INTO investory.accounts(external_account_id,currency,provider,name,owner,portfolio_id) "
                + "SELECT 'c0-overlap-test','USD','XTB','C0 overlap test','test',id "
                + "FROM investory.portfolios WHERE name='C0 overlap test'");
        statement.execute(
            "INSERT INTO investory.accounts(external_account_id,currency,provider,name,owner,portfolio_id) "
                + "SELECT 'c0-overlap-other','USD','XTB','C0 other account','test',id "
                + "FROM investory.portfolios WHERE name='C0 overlap test'");

        long firstImport = insertCompletedImport(statement, "a1".repeat(32), "overlap-first.zip");
        long secondImport = insertCompletedImport(statement, "a2".repeat(32), "overlap-second.zip");
        long firstFile = insertEvidenceFile(statement, firstImport, "a1".repeat(32), "first.xlsx");
        long secondFile =
            insertEvidenceFile(statement, secondImport, "a2".repeat(32), "second.xlsx");
        long linkedRow =
            insertEvidenceRow(
                statement,
                firstImport,
                firstFile,
                "c0-overlap-test/USD_first.xlsx",
                "hash-event",
                "d".repeat(64),
                "{}");
        long overlapRow =
            insertEvidenceRow(
                statement,
                secondImport,
                secondFile,
                "c0-overlap-test/USD_second.xlsx",
                "hash-event",
                "d".repeat(64),
                "{}");
        long originalCashRow =
            insertEvidenceRow(
                statement,
                firstImport,
                firstFile,
                "c0-overlap-test/USD_first.xlsx",
                "88002602",
                "f".repeat(64),
                "{\"value\":\"old\"}");
        long correctedCashRow =
            insertEvidenceRow(
                statement,
                secondImport,
                secondFile,
                "c0-overlap-test/USD_second.xlsx",
                "88002602",
                "0".repeat(64),
                "{\"value\":\"corrected\"}");
        long otherAccountSameIdRow =
            insertEvidenceRow(
                statement,
                secondImport,
                secondFile,
                "c0-overlap-other/USD_second.xlsx",
                "88002602",
                "1".repeat(64),
                "{\"value\":\"other account\"}");
        insertEvidenceRow(
            statement,
            secondImport,
            secondFile,
            "c0-overlap-test/USD_second.xlsx",
            "unmapped-event",
            "e".repeat(64),
            "{}");

        statement.execute(
            "INSERT INTO investory.cash_operations(id,account_id,operation,amount,currency,comment,date,import_history_id,import_source_row_id) "
                + "SELECT 88002601,id,'DEPOSIT',1,'USD','C0 overlap test',now(),"
                + firstImport
                + ","
                + linkedRow
                + " FROM investory.accounts WHERE external_account_id='c0-overlap-test'");
        statement.execute(
            "INSERT INTO investory.cash_operations(id,account_id,operation,amount,currency,comment,date,import_history_id,import_source_row_id) "
                + "SELECT 88002602,id,'DIVIDEND',1,'USD','C0 corrected XTB cash row',now(),"
                + firstImport
                + ","
                + originalCashRow
                + " FROM investory.accounts WHERE external_account_id='c0-overlap-test'");

        assertEquals(
            2,
            MigrationTestDatabase.singleInt(
                statement,
                "SELECT count(*) FROM investory.recon_v_import_provenance_issues "
                    + "WHERE issue_code='ORPHAN_SOURCE_ROW' AND import_history_id="
                    + secondImport));
        assertEquals(
            0,
            MigrationTestDatabase.singleInt(
                statement,
                "SELECT count(*) FROM investory.recon_v_import_provenance_issues "
                    + "WHERE issue_code='ORPHAN_SOURCE_ROW' AND financial_row_id='"
                    + overlapRow
                    + "'"));
        assertEquals(
            0,
            MigrationTestDatabase.singleInt(
                statement,
                "SELECT count(*) FROM investory.recon_v_import_provenance_issues "
                    + "WHERE issue_code='ORPHAN_SOURCE_ROW' AND financial_row_id='"
                    + correctedCashRow
                    + "'"));
        assertEquals(
            1,
            MigrationTestDatabase.singleInt(
                statement,
                "SELECT count(*) FROM investory.recon_v_import_provenance_issues "
                    + "WHERE issue_code='ORPHAN_SOURCE_ROW' AND financial_row_id='"
                    + otherAccountSameIdRow
                    + "'"));
      } finally {
        connection.rollback();
      }
    }
  }

  private static long insertCompletedImport(Statement statement, String checksum, String fileName)
      throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "INSERT INTO investory.import_history(provider,file_name,file_sha256,started_at,finished_at,status,rows_total,rows_applied,rows_failed) VALUES ('XTB','"
                + fileName
                + "','"
                + checksum
                + "',now(),now(),'COMPLETED',1,1,0) RETURNING id")) {
      result.next();
      return result.getLong(1);
    }
  }

  private static long insertEvidenceFile(
      Statement statement, long importId, String checksum, String fileName) throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "INSERT INTO investory.import_source_files(provider,import_history_id,file_name,content_type,file_sha256,original_size,raw_payload) VALUES ('XTB',"
                + importId
                + ",'"
                + fileName
                + "','application/octet-stream','"
                + checksum
                + "',1,decode('00','hex')) RETURNING id")) {
      result.next();
      return result.getLong(1);
    }
  }

  private static long insertEvidenceRow(
      Statement statement,
      long importId,
      long fileId,
      String member,
      String sourceRecordId,
      String logicalHash,
      String rawValues)
      throws Exception {
    try (ResultSet result =
        statement.executeQuery(
            "INSERT INTO investory.import_source_rows(import_history_id,source_file_id,provider,section_name,sheet_name,archive_member_name,source_row_number,source_record_id,source_row_occurrence,logical_row_sha256,raw_values) VALUES ("
                + importId
                + ","
                + fileId
                + ",'XTB','Cash Operations','Cash Operations','"
                + member
                + "',1,'"
                + sourceRecordId
                + "',1,'"
                + logicalHash
                + "','"
                + rawValues
                + "'::jsonb) RETURNING id")) {
      result.next();
      return result.getLong(1);
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
              + "SELECT 88000001,v.d,'TEST','TEMP.TEST',m.id,'MARKET_DATA',v.currency,v.price,'EXACT_LISTING_MARKET_CLOSE',true,false,1 "
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
