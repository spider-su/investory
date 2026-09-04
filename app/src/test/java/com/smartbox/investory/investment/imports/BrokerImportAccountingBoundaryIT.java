package com.smartbox.investory.investment.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartbox.investory.testsupport.FastDatabaseTest;
import com.smartbox.investory.testsupport.happyinvestor.HappyInvestorImportFacts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Proves that HTTP broker import ends at canonical accounting data. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DisplayName("Broker Import Accounting Boundary")
class BrokerImportAccountingBoundaryIT extends FastDatabaseTest {

  private static final long ACCOUNT_ID = HappyInvestorImportFacts.ACCOUNT_ID;
  private static final long PORTFOLIO_ID = HappyInvestorImportFacts.PORTFOLIO_ID;
  private static final String FILE_NAME = HappyInvestorImportFacts.FILE_NAME;
  private static final String STATEMENT = HappyInvestorImportFacts.STATEMENT;

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void removeBoundaryRows() {
    jdbc.execute("SET session_replication_role = replica");
    try {
      jdbc.update(
          "DELETE FROM investory.positions WHERE import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
          FILE_NAME);
      jdbc.update(
          "DELETE FROM investory.cash_operations WHERE import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
          FILE_NAME);
      jdbc.update(
          "DELETE FROM investory.import_source_rows WHERE import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
          FILE_NAME);
      jdbc.update(
          "DELETE FROM investory.import_source_files WHERE import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
          FILE_NAME);
      jdbc.update("DELETE FROM investory.import_history WHERE file_name = ?", FILE_NAME);
    } finally {
      jdbc.execute("SET session_replication_role = origin");
    }
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("multipart import persists accounting and immutable provenance without refresh")
  void multipartImportPersistsAccountingAndProvenanceWithoutRefresh() throws Exception {
    long dailyBefore =
        count("SELECT count(*) FROM investory.account_daily WHERE account_id = ?", ACCOUNT_ID);
    byte[] bytes = STATEMENT.getBytes(StandardCharsets.UTF_8);

    importFile(bytes)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.duplicate").value(false));

    assertThat(
            jdbc.queryForObject(
                "SELECT portfolio_id FROM investory.accounts WHERE id = ?", Long.class, ACCOUNT_ID))
        .isEqualTo(PORTFOLIO_ID);
    Map<String, Object> deposit =
        jdbc.queryForMap(
            "SELECT operation, amount, currency, date::date business_date, import_history_id, import_source_row_id FROM investory.cash_operations WHERE account_id = ? AND operation = 'DEPOSIT' AND amount = 100000 AND date::date = DATE '2024-07-31' AND import_history_id IS NOT NULL",
            ACCOUNT_ID);
    assertThat(deposit)
        .containsEntry("operation", "DEPOSIT")
        .containsEntry("currency", "USD")
        .containsEntry("business_date", java.sql.Date.valueOf("2024-07-31"));
    assertThat(deposit.get("import_history_id")).isNotNull();
    assertThat(deposit.get("import_source_row_id")).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.positions WHERE account_id = ? AND source_asset_symbol = 'VWRA.UK' AND operation = 'BUY' AND volume = 20",
                Long.class,
                ACCOUNT_ID))
        .isEqualTo(1);
    Map<String, Object> vwra =
        jdbc.queryForMap(
            "SELECT volume, price_currency, purchase_value, commission, commission_currency FROM investory.positions WHERE account_id = ? AND source_asset_symbol = 'VWRA.UK' AND operation = 'BUY' AND volume = 20",
            ACCOUNT_ID);
    assertThat(vwra)
        .containsEntry("price_currency", "USD")
        .containsEntry("commission_currency", "USD");
    assertThat((java.math.BigDecimal) vwra.get("purchase_value")).isEqualByComparingTo("2400.00");
    assertThat((java.math.BigDecimal) vwra.get("commission")).isEqualByComparingTo("-1.00");
    long batchId = ((Number) deposit.get("import_history_id")).longValue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_history WHERE id = ? AND portfolio_id = ? AND status = 'COMPLETED'",
                Long.class,
                batchId,
                PORTFOLIO_ID))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_source_files WHERE import_history_id = ? AND file_sha256 = ? AND original_size = ?",
                Long.class,
                batchId,
                sha256(bytes),
                bytes.length))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_source_rows WHERE import_history_id = ? AND source_file_id IS NOT NULL",
                Long.class,
                batchId))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_source_rows r JOIN investory.cash_operations c ON c.import_source_row_id = r.id WHERE c.import_history_id = ?",
                Long.class,
                batchId))
        .isGreaterThan(0);
    assertThat(
            count("SELECT count(*) FROM investory.account_daily WHERE account_id = ?", ACCOUNT_ID))
        .isEqualTo(dailyBefore);

    importFile(bytes).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_history WHERE file_name = ?",
                Long.class,
                FILE_NAME))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.cash_operations WHERE account_id = ? AND amount IN (100000, -18001, -2401) AND import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
                Long.class,
                ACCOUNT_ID,
                FILE_NAME))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "SELECT min(attempt_no) FROM investory.import_history WHERE file_name = ?",
                Integer.class,
                FILE_NAME))
        .isEqualTo(1);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("malformed multipart import keeps failed audit and rolls back accounting")
  void malformedMultipartImportKeepsFailedAuditAndRollsBackAccounting() throws Exception {
    byte[] bytes = "not an IBKR statement".getBytes(StandardCharsets.UTF_8);
    importFile(bytes).andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.import_history WHERE file_name = ? AND status = 'FAILED'",
                Long.class,
                FILE_NAME))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.cash_operations WHERE import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
                Long.class,
                FILE_NAME))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.positions WHERE import_history_id IN (SELECT id FROM investory.import_history WHERE file_name = ?)",
                Long.class,
                FILE_NAME))
        .isZero();
  }

  private org.springframework.test.web.servlet.ResultActions importFile(byte[] bytes)
      throws Exception {
    return mockMvc.perform(
        multipart("/api/v1/investment/imports/broker/IBKR")
            .file(new MockMultipartFile("file", FILE_NAME, MediaType.TEXT_PLAIN_VALUE, bytes))
            .param("portfolioId", String.valueOf(PORTFOLIO_ID))
            .param("deferRefresh", "true")
            .with(user("admin").roles("ADMIN"))
            .with(csrf()));
  }

  private long count(String sql, long value) {
    return jdbc.queryForObject(sql, Long.class, value);
  }

  private static String sha256(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
