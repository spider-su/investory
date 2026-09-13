package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.accounting.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService;
import com.smartbox.investory.accounting.staging.AccountingStagingRepository;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database contract for the reviewed-document staging and promotion boundary. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AccountingStagingFlowCurrentIT extends AccountingDatabaseTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 3, 1);

  @Autowired private AccountingSourceEvidenceService sources;
  @Autowired private AccountingStagingAcquisitionService acquisition;
  @Autowired private AccountingStagingRepository repository;

  @Autowired
  @Qualifier("accountingStagingFacade")
  private AccountingStagingApi staging;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void reviewedInvoiceStagesReconcilesPromotesAndWritesVatFact() {
    long sourceId =
        sources.receiveUpload("staging-flow.pdf", "application/pdf", new byte[] {3, 2, 1});
    acquisition.stageInvoice(1, invoice("STAGING-FLOW-2026-03", sourceId), "DOMESTIC_VAT");

    assertThat(staging.reconcile(1, java.time.YearMonth.of(2026, 3)).readyToPromote()).isEqualTo(1);
    assertThat(staging.promote(1, java.time.YearMonth.of(2026, 3)).invoices()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.accounting_poc_invoice WHERE reference='STAGING-FLOW-2026-03'",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT treatment FROM investory.accounting_vat_transaction WHERE reference='STAGING-FLOW-2026-03'",
                String.class))
        .isEqualTo("DOMESTIC_VAT");
    assertThat(sources.status(sourceId)).isEqualTo(AccountingSourceStatus.IMPORTED);
  }

  @Test
  void unknownDirectionAndMissingVatTreatmentNeverEnterStaging() {
    long sourceId =
        sources.receiveUpload("invalid-staging.pdf", "application/pdf", new byte[] {4, 5, 6});
    assertThatThrownBy(
            () ->
                acquisition.stageInvoice(
                    1, invoiceWithType("UNKNOWN", "UNKNOWN-2026-03", sourceId), "DOMESTIC_VAT"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> acquisition.stageInvoice(1, invoice("MISSING-VAT-2026-03", sourceId), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM investory.accounting_tmp_invoice WHERE source_id=?",
                Integer.class,
                sourceId))
        .isZero();
  }

  private ReviewedInvoice invoice(String reference, long sourceId) {
    return invoiceWithType("SALES_INVOICE", reference, sourceId);
  }

  private ReviewedInvoice invoiceWithType(String type, String reference, long sourceId) {
    return new ReviewedInvoice(
        PERIOD,
        type,
        PERIOD,
        PERIOD,
        reference,
        "Staging customer",
        "CONSULTING",
        "PLN",
        new BigDecimal("100.00"),
        new BigDecimal("23.00"),
        new BigDecimal("123.00"),
        BigDecimal.ONE,
        "REVIEWED",
        "Golden staged invoice",
        Long.toString(sourceId));
  }
}
