package com.smartbox.investory.poc.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.AccountingBankImportService;
import com.smartbox.investory.accounting.AccountingFactService;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BankTransactionProviderNeutralIT extends AccountingDatabaseTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);

  @Autowired private AccountingBankImportService bankImport;
  @Autowired private AccountingFactService factService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void insertOperationalProfile() {
    jdbcTemplate.update(
        "INSERT INTO investory.employment_period (profile_id, employment_type, date_from) VALUES (1, 'JDG', ?)"
            + " ON CONFLICT DO NOTHING",
        PERIOD);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_tax_profile_period (profile_id, valid_from, jdg_active, ryczalt_rate, vat_registered, vat_eu_registered, zus_regime, voluntary_sickness) VALUES (1, ?, true, 0.12, true, true, 'JDG', false)"
            + " ON CONFLICT DO NOTHING",
        PERIOD);
  }

  @AfterEach
  void removeFixture() {
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_bank_transaction WHERE reference LIKE 'FREEZE-%'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_source_evidence WHERE original_filename LIKE 'freeze-%'");
    jdbcTemplate.update(
        "DELETE FROM investory.employment_period WHERE profile_id = 1 AND date_from = ?", PERIOD);
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_tax_profile_period WHERE profile_id = 1 AND valid_from = ?",
        PERIOD);
  }

  @Test
  void csvSourceIsIdempotentAndRetainsUnknownTransactions() {
    byte[] first = csv("FREEZE-UNKNOWN", "UNKNOWN CARD", "-12.34").getBytes();
    byte[] sameTransactionInSecondFile =
        (csv("FREEZE-UNKNOWN", "UNKNOWN CARD", "-12.34") + "\n").getBytes();

    bankImport.importFile("freeze-first.csv", "text/csv", first, PERIOD);
    bankImport.importFile("freeze-second.csv", "text/csv", sameTransactionInSecondFile, PERIOD);

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
    var before = factService.snapshot(PERIOD);
    BigDecimal totalZus = before.zus().totalZus();
    assertThat(totalZus).isPositive();

    bankImport.importFile(
        "freeze-zus.csv",
        "text/csv",
        csv("FREEZE-ZUS", "ZUS", totalZus.negate().toPlainString()).getBytes(),
        PERIOD);

    var after = factService.snapshot(PERIOD);
    assertThat(after.zus().healthZus()).isPositive();
    assertThat(after.ryczalt().healthContributionPaid())
        .isEqualByComparingTo(after.zus().healthZus());
    assertThat(after.ryczalt().healthDeduction())
        .isEqualByComparingTo(after.zus().healthZus().divide(new BigDecimal("2")));
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
