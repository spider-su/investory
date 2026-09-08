package com.smartbox.investory.investment.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ImportOriginLookupServiceIT extends FastDatabaseTest {
  private static final long BATCH_ID = 9_900_001L;
  private static final long FILE_ID = 9_900_002L;
  private static final long ROW_ID = 9_900_003L;
  private static final long CASH_ID = 9_900_004L;
  private static final long POSITION_ID = 9_900_005L;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ImportOriginLookupService lookup;

  @BeforeEach
  void insertProvenanceFixture() {
    jdbc.update("delete from investory.cash_operations where id in (?, ?)", CASH_ID, POSITION_ID);
    jdbc.update("delete from investory.positions where id = ?", POSITION_ID);
    jdbc.update("delete from investory.import_source_rows where id = ?", ROW_ID);
    jdbc.update("delete from investory.import_source_files where id = ?", FILE_ID);
    jdbc.update("delete from investory.import_history where id = ?", BATCH_ID);

    var accountId =
        jdbc.queryForObject("select id from investory.accounts order by id limit 1", Long.class);
    var assetId =
        jdbc.queryForObject("select id from investory.assets order by id limit 1", Long.class);
    var instant = ZonedDateTime.parse("2026-09-08T10:00:00Z");
    jdbc.update(
        "insert into investory.import_history (id, provider, file_name, file_sha256, started_at, finished_at, status, attempt_no) values (?, 'IBKR', 'evidence.csv', ?, ?, ?, 'COMPLETED', 1)",
        BATCH_ID,
        "a".repeat(64),
        Timestamp.from(instant.toInstant()),
        Timestamp.from(instant.plusMinutes(1).toInstant()));
    jdbc.update(
        "insert into investory.import_source_files (id, provider, import_history_id, file_name, file_sha256, original_size, raw_payload) values (?, 'IBKR', ?, 'evidence.csv', ?, 7, decode('616263', 'hex'))",
        FILE_ID,
        BATCH_ID,
        "b".repeat(64));
    jdbc.update(
        "insert into investory.import_source_rows (id, import_history_id, source_file_id, provider, section_name, source_row_number, source_row_occurrence, raw_values) values (?, ?, ?, 'IBKR', 'Trades', 42, 1, '{\"symbol\":\"ABC\"}')",
        ROW_ID,
        BATCH_ID,
        FILE_ID);
    jdbc.update(
        "insert into investory.cash_operations (id, account_id, operation, amount, currency, date, import_history_id, import_source_row_id) values (?, ?, 'DEPOSIT'::investory.cash_operation_type, 12.34, 'USD', ?, ?, ?)",
        CASH_ID,
        accountId,
        Timestamp.from(instant.toInstant()),
        BATCH_ID,
        ROW_ID);
    jdbc.update(
        "insert into investory.positions (id, account_id, asset_id, source_asset_symbol, operation, volume, price_currency, cost_currency, profit_currency, commission_currency, open_time, open_price, import_history_id, import_source_row_id) values (?, ?, ?, 'ABC', 'BUY'::investory.positions_operation_type, 1, 'USD', 'USD', 'USD', 'USD', ?, 10, ?, ?)",
        POSITION_ID,
        accountId,
        assetId,
        Timestamp.from(instant.toInstant()),
        BATCH_ID,
        ROW_ID);
  }

  @Test
  void tracesCashAndPositionToImmutableImportEvidence() {
    var cash = lookup.findCashOperationOrigin(CASH_ID).orElseThrow();
    var position = lookup.findPositionOrigin(POSITION_ID).orElseThrow();

    assertEquals(CASH_ID, cash.financialRowId());
    assertEquals("cash_operations", cash.financialTable());
    assertEquals(BATCH_ID, cash.importBatchId());
    assertEquals("evidence.csv", cash.fileName());
    assertEquals("Trades", cash.sectionName());
    assertEquals(42, cash.sourceRowNumber());
    assertEquals(POSITION_ID, position.financialRowId());
    assertEquals("positions", position.financialTable());
    assertEquals(BATCH_ID, position.importBatchId());
    assertEquals("{\"symbol\": \"ABC\"}", position.rawValues());
  }

  @Test
  void unknownFinancialRowHasNoOrigin() {
    assertTrue(lookup.findCashOperationOrigin(-1L).isEmpty());
    assertTrue(lookup.findPositionOrigin(-1L).isEmpty());
  }
}
