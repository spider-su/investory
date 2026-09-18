package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.service.AccountingBankImportService;
import com.smartbox.investory.accounting.service.AccountingFactService;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.accounting.AccountingDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test-fast")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BankTransactionProviderNeutralIT {
  private static final WorkerDatabase DATABASE =
      AccountingDatabase.scopedPocDatabase("bank_transaction_provider_neutral");
  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);

  @Autowired private AccountingBankImportService bankImport;
  @Autowired private AccountingFactService factService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  protected static void providerDatabaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @BeforeEach
  void insertOperationalProfile() {
    jdbcTemplate.update(
        "INSERT INTO investory.employment_period (profile_id, employment_type, date_from, qualifies_as_primary_social_insurance) VALUES (1, 'UOP', ?, true)"
            + " ON CONFLICT DO NOTHING",
        PERIOD);
    jdbcTemplate.update(
        "INSERT INTO investory.employment_period (profile_id, employment_type, date_from, qualifies_as_primary_social_insurance) VALUES (1, 'JDG', ?, false)"
            + " ON CONFLICT DO NOTHING",
        PERIOD);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_tax_profile_period (profile_id, valid_from, jdg_active, ryczalt_rate, vat_registered, vat_eu_registered, zus_regime, voluntary_sickness) VALUES (1, ?, true, 0.12, true, true, 'JDG', false)"
            + " ON CONFLICT DO NOTHING",
        PERIOD);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (profile_id, tax_period, input_type, amount, note) VALUES (1, ?, 'HEALTH_CONTRIBUTION_PAID', 1495.04, 'FREEZE_TEST') ON CONFLICT DO NOTHING",
        PERIOD);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_invoice (profile_id, tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind, currency, net_amount, vat_amount, gross_amount, expected_receivable, booked_net_pln, ryczalt_rate, note) VALUES (1, ?, ?, ?, 'FREEZE-SALE', 'FREEZE CUSTOMER', 'SALES_INVOICE', 'PLN', 100.00, 23.00, 123.00, 123.00, 100.00, 0.12, 'FREEZE_TEST') ON CONFLICT DO NOTHING",
        PERIOD,
        PERIOD.plusDays(1),
        PERIOD.plusDays(1));
  }

  @AfterEach
  void removeFixture() {
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_bank_transaction WHERE reference LIKE 'FREEZE-%'");
    jdbcTemplate.update(
        "DELETE FROM investory.employment_period WHERE profile_id = 1 AND date_from = ?", PERIOD);
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_tax_profile_period WHERE profile_id = 1 AND valid_from = ?",
        PERIOD);
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_tax_input WHERE profile_id = 1 AND tax_period = ? AND note = 'FREEZE_TEST'",
        PERIOD);
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_invoice WHERE profile_id = 1 AND reference = 'FREEZE-SALE'");
  }

  @Test
  void csvSourceIsIdempotentAndRetainsUnknownTransactions() {
    byte[] first = csv("FREEZE-UNKNOWN", "UNKNOWN CARD", "-12.34").getBytes();
    byte[] sameTransactionInSecondFile =
        (csv("FREEZE-UNKNOWN", "UNKNOWN CARD", "-12.34") + "\n").getBytes();

    bankImport.importFile(1L, "freeze-first.csv", "text/csv", first, PERIOD);
    bankImport.importFile(1L, "freeze-second.csv", "text/csv", sameTransactionInSecondFile, PERIOD);

    var row =
        jdbcTemplate.queryForMap(
            "SELECT provider, external_account_id, external_transaction_id, source_payload_hash, transaction_type FROM investory.accounting_poc_bank_transaction WHERE reference = 'FREEZE-UNKNOWN'");
    assertThat(row)
        .containsEntry("provider", "CSV")
        .containsEntry("external_account_id", "JDG_MAIN_ACCOUNT")
        .containsEntry("transaction_type", "UNKNOWN")
        .doesNotContainEntry("source_payload_hash", null);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM investory.accounting_poc_bank_transaction WHERE reference = 'FREEZE-UNKNOWN'",
                Integer.class))
        .isOne();
  }

  @Test
  void zusPaymentFlowsIntoContributionProjectionAndHealthDeduction() {
    BigDecimal totalZus = new BigDecimal("1495.04");

    bankImport.importFile(
        1L,
        "freeze-zus.csv",
        "text/csv",
        csv("FREEZE-ZUS", "ZUS", totalZus.negate().toPlainString()).getBytes(),
        PERIOD);

    var after = factService.snapshot(1L, PERIOD);
    assertThat(after.ryczalt().healthContributionPaid()).isPositive();
    assertThat(after.ryczalt().healthDeduction()).isEqualByComparingTo(new BigDecimal("100.00"));
  }

  private String csv(String reference, String counterparty, String amount) {
    return "booking_date;related_period;reference;counterparty;currency;amount;note\n"
        + "2026-09-15;2026-09-01;"
        + reference
        + ";"
        + counterparty
        + ";PLN;"
        + amount
        + ";freeze test\n";
  }
}
