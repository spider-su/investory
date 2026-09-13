package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.staging.AccountingStagingPromotionService;
import com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService;
import com.smartbox.investory.accounting.staging.AccountingStagingRepository;
import com.smartbox.investory.accounting.staging.StagedInvoice;
import com.smartbox.investory.accounting.staging.StagingReconciliationStatus;
import com.smartbox.investory.testsupport.FastDatabase;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** DB-backed contract for source staging, reconciliation and explicit promotion. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test-fast")
@DisplayName("Accounting staging flow")
class AccountingStagingFlowIT extends AccountingDatabaseTest {
  private static final WorkerDatabase DATABASE =
      FastDatabase.scopedDatabase("accounting_staging_flow");

  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);
  private static final long PROFILE_A = -930001L;
  private static final long PROFILE_B = -930002L;
  private static final String SOURCE_PREFIX = "staging-it-";
  private static final String REFERENCE_PREFIX = "STAGING-IT-";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccountingSourceRepository sourceRepository;
  @Autowired private AccountingStagingRepository staging;
  @Autowired private AccountingStagingReconciliationService reconciliation;
  @Autowired private AccountingStagingPromotionService promotion;

  @AfterAll
  static void closeDatabase() {
    DATABASE.close();
  }

  @DynamicPropertySource
  protected static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", DATABASE::jdbcUrl);
    registry.add("spring.datasource.username", DATABASE::username);
    registry.add("spring.datasource.password", DATABASE::password);
  }

  @AfterEach
  void cleanFixture() {
    jdbc.update(
        "DELETE FROM investory.accounting_vat_transaction WHERE reference LIKE ?",
        REFERENCE_PREFIX + "%");
    jdbc.update(
        "DELETE FROM investory.accounting_tmp_invoice WHERE source_id IN "
            + "(SELECT id FROM investory.accounting_source_evidence WHERE external_reference LIKE ?)",
        SOURCE_PREFIX + "%");
    jdbc.update(
        "DELETE FROM investory.accounting_tmp_bank_transaction WHERE source_id IN "
            + "(SELECT id FROM investory.accounting_source_evidence WHERE external_reference LIKE ?)",
        SOURCE_PREFIX + "%");
    jdbc.update(
        "DELETE FROM investory.accounting_poc_expense_invoice WHERE reference LIKE ?",
        REFERENCE_PREFIX + "%");
    jdbc.update(
        "DELETE FROM investory.accounting_poc_invoice WHERE reference LIKE ?",
        REFERENCE_PREFIX + "%");
    jdbc.update(
        "DELETE FROM investory.accounting_poc_bank_transaction WHERE external_transaction_id LIKE ?",
        SOURCE_PREFIX + "%");
    jdbc.update(
        "DELETE FROM investory.accounting_source_evidence WHERE external_reference LIKE ?",
        SOURCE_PREFIX + "%");
  }

  @Test
  @DisplayName("NEW invoice is reconciled and explicitly promoted into canonical facts")
  void newInvoiceReconcilesAndPromotes() {
    long sourceId = source("invoice-a");
    long stageId = stageInvoice(PROFILE_A, sourceId, "INV-A");

    var summary = reconciliation.reconcile(PROFILE_A, PERIOD);

    assertThat(summary.invoiceNew()).isEqualTo(1);
    assertThat(summary.readyToPromote()).isEqualTo(1);
    assertThat(stagedInvoice(stageId).status()).isEqualTo(StagingReconciliationStatus.NEW);

    var promoted = promotion.promoteNew(PROFILE_A, PERIOD);
    var row = stagedInvoice(stageId);

    assertThat(promoted.invoices()).isEqualTo(1);
    assertThat(promoted.bankTransactions()).isZero();
    assertThat(row.status()).isEqualTo(StagingReconciliationStatus.PROMOTED);
    assertThat(row.canonicalId()).isNotNull();
    assertThat(row.promotedAt()).isNotNull();
    assertThat(canonicalInvoiceCount("INV-A")).isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.accounting_vat_transaction WHERE reference = ?",
                Long.class,
                reference("INV-A")))
        .isEqualTo(1L);
    assertThat(sourceRepository.status(sourceId)).isEqualTo(AccountingSourceStatus.IMPORTED);
  }

  @Test
  @DisplayName("reconciliation and promotion operate only on the requested staging profile")
  void twoProfilesRemainIsolatedThroughReconciliationAndPromotion() {
    long sourceA = source("profile-a");
    long sourceB = source("profile-b");
    long stageA = stageInvoice(PROFILE_A, sourceA, "PROFILE-A");
    long stageB = stageInvoice(PROFILE_B, sourceB, "PROFILE-B");

    var summaryA = reconciliation.reconcile(PROFILE_A, PERIOD);

    assertThat(summaryA.invoiceNew()).isEqualTo(1);
    assertThat(stagedInvoice(stageA).status()).isEqualTo(StagingReconciliationStatus.NEW);
    assertThat(stagedInvoice(stageB).status()).isEqualTo(StagingReconciliationStatus.PENDING);

    var promotedA = promotion.promoteNew(PROFILE_A, PERIOD);

    assertThat(promotedA.invoices()).isEqualTo(1);
    assertThat(stagedInvoice(stageA).status()).isEqualTo(StagingReconciliationStatus.PROMOTED);
    assertThat(stagedInvoice(stageB).status()).isEqualTo(StagingReconciliationStatus.PENDING);
    assertThat(canonicalInvoiceCount("PROFILE-B")).isZero();
    assertThat(sourceRepository.status(sourceA)).isEqualTo(AccountingSourceStatus.IMPORTED);
    assertThat(sourceRepository.status(sourceB)).isEqualTo(AccountingSourceStatus.RECEIVED);

    var summaryB = reconciliation.reconcile(PROFILE_B, PERIOD);
    assertThat(summaryB.invoiceNew()).isEqualTo(1);
    assertThat(stagedInvoice(stageB).status()).isEqualTo(StagingReconciliationStatus.NEW);
  }

  @Test
  @DisplayName("canonical invoice owned by another profile must not match staged input")
  void canonicalFactsFromAnotherProfileDoNotContaminateReconciliation() {
    long sourceA = source("canonical-profile-a");
    stageInvoice(PROFILE_A, sourceA, "CROSS-PROFILE");
    reconciliation.reconcile(PROFILE_A, PERIOD);
    promotion.promoteNew(PROFILE_A, PERIOD);
    assertThat(canonicalInvoiceCount("CROSS-PROFILE")).isEqualTo(1L);

    long sourceB = source("staged-profile-b");
    long stageB = stageInvoice(PROFILE_B, sourceB, "CROSS-PROFILE");

    var summaryB = reconciliation.reconcile(PROFILE_B, PERIOD);

    assertThat(summaryB.invoiceNew()).isEqualTo(1);
    assertThat(summaryB.invoiceMatched()).isZero();
    assertThat(stagedInvoice(stageB).status()).isEqualTo(StagingReconciliationStatus.NEW);
  }

  @Test
  @DisplayName("MATCH canonical invoice is not promoted again")
  void matchingCanonicalInvoiceIsNotPromoted() {
    long sourceId = source("canonical-match");
    long firstStage = stageInvoice(PROFILE_A, sourceId, "MATCH");
    reconciliation.reconcile(PROFILE_A, PERIOD);
    promotion.promoteNew(PROFILE_A, PERIOD);
    assertThat(stagedInvoice(firstStage).status()).isEqualTo(StagingReconciliationStatus.PROMOTED);

    long secondSource = source("staged-match");
    long secondStage = stageInvoice(PROFILE_A, secondSource, "MATCH");

    var summary = reconciliation.reconcile(PROFILE_A, PERIOD);

    assertThat(summary.invoiceMatched()).isEqualTo(1);
    assertThat(stagedInvoice(secondStage).status()).isEqualTo(StagingReconciliationStatus.MATCH);

    var promoted = promotion.promoteNew(PROFILE_A, PERIOD);

    assertThat(promoted.invoices()).isZero();
    assertThat(stagedInvoice(secondStage).status()).isEqualTo(StagingReconciliationStatus.MATCH);
    assertThat(canonicalInvoiceCount("MATCH")).isEqualTo(1L);
  }

  @Test
  @DisplayName("MISMATCH is blocked from promotion")
  void mismatchedCanonicalInvoiceIsBlocked() {
    long canonicalSource = source("canonical-mismatch");
    stageInvoice(PROFILE_A, canonicalSource, "MISMATCH");
    reconciliation.reconcile(PROFILE_A, PERIOD);
    promotion.promoteNew(PROFILE_A, PERIOD);
    jdbc.update(
        "UPDATE investory.accounting_poc_invoice SET gross_amount = 9999.99 WHERE reference = ?",
        reference("MISMATCH"));

    long stagedSource = source("staged-mismatch");
    long stagedId = stageInvoice(PROFILE_A, stagedSource, "MISMATCH");

    var summary = reconciliation.reconcile(PROFILE_A, PERIOD);

    assertThat(summary.invoiceMismatch()).isEqualTo(1);
    assertThat(stagedInvoice(stagedId).status()).isEqualTo(StagingReconciliationStatus.MISMATCH);
    assertThat(stagedInvoice(stagedId).reasonCodes()).contains("GROSS_MISMATCH");

    var promoted = promotion.promoteNew(PROFILE_A, PERIOD);
    assertThat(promoted.invoices()).isZero();
    assertThat(canonicalInvoiceCount("MISMATCH")).isEqualTo(1L);
  }

  @Test
  @DisplayName("AMBIGUOUS canonical candidates are blocked from promotion")
  void ambiguousCanonicalInvoiceIsBlocked() {
    long sourceOne = source("ambiguous-one");
    stageInvoice(PROFILE_A, sourceOne, "AMBIGUOUS-REF");
    reconciliation.reconcile(PROFILE_A, PERIOD);
    promotion.promoteNew(PROFILE_A, PERIOD);

    long sourceTwo = source("ambiguous-two");
    stageInvoice(PROFILE_A, sourceTwo, "AMBIGUOUS-KSEF");
    reconciliation.reconcile(PROFILE_A, PERIOD);
    promotion.promoteNew(PROFILE_A, PERIOD);
    jdbc.update(
        "UPDATE investory.accounting_poc_invoice SET ksef_number = ? WHERE reference = ?",
        "STAGING-IT-KSEF-DUPLICATE",
        reference("AMBIGUOUS-KSEF"));

    long stagedSource = source("ambiguous-staged");
    long stagedId =
        stageInvoice(PROFILE_A, stagedSource, "AMBIGUOUS-REF", "STAGING-IT-KSEF-DUPLICATE");

    var summary = reconciliation.reconcile(PROFILE_A, PERIOD);

    assertThat(summary.invoiceAmbiguous()).isEqualTo(1);
    assertThat(stagedInvoice(stagedId).status()).isEqualTo(StagingReconciliationStatus.AMBIGUOUS);
    assertThat(stagedInvoice(stagedId).reasonCodes()).contains("MULTIPLE_CANONICAL_MATCHES");

    var promoted = promotion.promoteNew(PROFILE_A, PERIOD);
    assertThat(promoted.invoices()).isZero();
  }

  @Test
  @DisplayName("promotion is idempotent and PROMOTED remains terminal")
  void repeatedPromotionDoesNotDuplicateOrRewritePromotedState() {
    long sourceId = source("idempotent");
    long stagedId = stageInvoice(PROFILE_A, sourceId, "IDEMPOTENT");

    var first = promotion.promoteNew(PROFILE_A, PERIOD);
    var second = promotion.promoteNew(PROFILE_A, PERIOD);

    assertThat(first.invoices()).isEqualTo(1);
    assertThat(second.invoices()).isZero();
    assertThat(canonicalInvoiceCount("IDEMPOTENT")).isEqualTo(1L);
    assertThat(stagedInvoice(stagedId).status()).isEqualTo(StagingReconciliationStatus.PROMOTED);
    assertThat(stagedInvoice(stagedId).canonicalId()).isNotNull();
    assertThat(stagedInvoice(stagedId).promotedAt()).isNotNull();
  }

  @Test
  @DisplayName("NEW bank transaction is reconciled and promoted once")
  void newBankTransactionReconcilesAndPromotes() {
    long sourceId = source("bank-a");
    long stageId =
        staging.insertBank(
            PROFILE_A,
            PERIOD,
            sourceId,
            "BANK",
            SOURCE_PREFIX + "bank-a",
            "CSV",
            "staging-it-account",
            SOURCE_PREFIX + "bank-tx-a",
            LocalDate.of(2026, 9, 10),
            LocalDate.of(2026, 9, 10),
            new BigDecimal("-123.45"),
            "PLN",
            "Test Supplier",
            "PL001",
            "SUPPLIER PAYMENT STAGING IT",
            "staging-it-hash-a");

    var summary = reconciliation.reconcile(PROFILE_A, PERIOD);

    assertThat(summary.bankNew()).isEqualTo(1);
    assertThat(stagedBank(stageId).status()).isEqualTo(StagingReconciliationStatus.NEW);

    var promoted = promotion.promoteNew(PROFILE_A, PERIOD);

    assertThat(promoted.bankTransactions()).isEqualTo(1);
    assertThat(stagedBank(stageId).status()).isEqualTo(StagingReconciliationStatus.PROMOTED);
    assertThat(stagedBank(stageId).canonicalId()).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.accounting_poc_bank_transaction WHERE external_transaction_id = ?",
                Long.class,
                SOURCE_PREFIX + "bank-tx-a"))
        .isEqualTo(1L);
  }

  private long source(String suffix) {
    String externalReference = SOURCE_PREFIX + suffix;
    byte[] payload = externalReference.getBytes(StandardCharsets.UTF_8);
    return sourceRepository.save(
        AccountingSourceType.UPLOAD,
        externalReference,
        externalReference + ".json",
        "application/json",
        Instant.parse("2026-09-13T10:00:00Z"),
        PERIOD,
        payload,
        payload);
  }

  private long stageInvoice(long profileId, long sourceId, String suffix) {
    return stageInvoice(profileId, sourceId, suffix, null);
  }

  private long stageInvoice(long profileId, long sourceId, String suffix, String ksefNumber) {
    return staging.insertInvoice(
        profileId,
        PERIOD,
        sourceId,
        "UPLOAD",
        SOURCE_PREFIX + suffix.toLowerCase(),
        "INCOME",
        LocalDate.of(2026, 9, 5),
        reference(suffix),
        "Test Customer",
        "DE123456789",
        "DE",
        "PLN",
        new BigDecimal("1000.00"),
        new BigDecimal("230.00"),
        new BigDecimal("1230.00"),
        null,
        BigDecimal.ZERO,
        VatTreatment.DOMESTIC_VAT.name(),
        ksefNumber);
  }

  private String reference(String suffix) {
    return REFERENCE_PREFIX + suffix;
  }

  private long canonicalInvoiceCount(String suffix) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM investory.accounting_poc_invoice WHERE reference = ?",
        Long.class,
        reference(suffix));
  }

  private StagedInvoice stagedInvoice(long id) {
    return staging.invoices(PROFILE_A, PERIOD).stream()
        .filter(row -> row.id() == id)
        .findFirst()
        .orElseGet(
            () ->
                staging.invoices(PROFILE_B, PERIOD).stream()
                    .filter(row -> row.id() == id)
                    .findFirst()
                    .orElseThrow());
  }

  private com.smartbox.investory.accounting.staging.StagedBankTransaction stagedBank(long id) {
    return staging.bankTransactions(PROFILE_A, PERIOD).stream()
        .filter(row -> row.id() == id)
        .findFirst()
        .orElseThrow();
  }
}
