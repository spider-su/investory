package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartbox.investory.accounting.api.AccountingStagingApi;
import com.smartbox.investory.accounting.infrastructure.persistence.AccountingStagingRepository;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.staging.AccountingBankStagingImportService;
import com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
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
  @Autowired private AccountingBankStagingImportService bankImport;

  @Autowired
  @Qualifier("accountingStagingFacade")
  private AccountingStagingApi staging;

  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void removeOperationalFixtures() {
    jdbc.update(
        "DELETE FROM investory.accounting_vat_transaction WHERE reference LIKE 'STAGING-FLOW-%'");
    jdbc.update(
        "DELETE FROM investory.accounting_poc_invoice WHERE reference LIKE 'STAGING-FLOW-%'");
    jdbc.update(
        "DELETE FROM investory.accounting_tmp_invoice WHERE reference LIKE 'STAGING-FLOW-%'");
    jdbc.update(
        "DELETE FROM investory.accounting_tmp_bank_transaction WHERE source_reference IN ('multi-month.csv', 'overlap.csv')");
  }

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

  @Test
  void oneBankExportRoutesByBookingMonthAndOverlappingExportsStayIdempotent() {
    byte[] export = bankCsv("MM-JAN", "MM-FEB", "MM-AUG");
    var first = bankImport.stageFile(1, "multi-month.csv", "text/csv", export, PERIOD);

    assertThat(first.processedRows()).isEqualTo(3);
    assertThat(first.stagedRows()).isEqualTo(3);
    assertThat(monthRows())
        .containsExactlyInAnyOrder(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 8, 1));

    var repeated = bankImport.stageFile(1, "multi-month.csv", "text/csv", export, PERIOD);
    assertThat(repeated.sourceId()).isEqualTo(first.sourceId());
    assertThat(monthRows()).hasSize(3);

    bankImport.stageFile(1, "overlap.csv", "text/csv", bankCsv("MM-JAN", "MM-NEW-MARCH"), PERIOD);
    assertThat(monthRows())
        .containsExactlyInAnyOrder(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 8, 1));
  }

  private byte[] bankCsv(String firstReference, String secondReference, String... moreReferences) {
    LocalDate secondDate =
        secondReference.equals("MM-NEW-MARCH")
            ? LocalDate.of(2026, 3, 1)
            : LocalDate.of(2026, 2, 2);
    StringBuilder csv =
        new StringBuilder(
            "booking_date;related_period;reference;counterparty;currency;amount;note\n"
                + "2026-01-31;2026-01-01;"
                + firstReference
                + ";Customer;PLN;100.00;receipt\n"
                + secondDate
                + ";"
                + secondDate.withDayOfMonth(1)
                + ";"
                + secondReference
                + ";Customer;PLN;200.00;receipt\n");
    for (String reference : moreReferences) {
      csv.append("2026-08-31;2026-08-01;")
          .append(reference)
          .append(";Customer;PLN;300.00;receipt\n");
    }
    return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private java.util.List<LocalDate> monthRows() {
    return jdbc.query(
        "SELECT tax_period FROM investory.accounting_tmp_bank_transaction WHERE source_reference IN ('multi-month.csv', 'overlap.csv') ORDER BY tax_period",
        (rs, rowNum) -> rs.getObject("tax_period", LocalDate.class));
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
