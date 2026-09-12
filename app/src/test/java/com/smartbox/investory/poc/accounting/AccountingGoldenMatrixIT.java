package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.accounting.testsupport.AccountingDatabaseTest;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AccountingGoldenMatrixIT extends AccountingDatabaseTest {

  @Autowired private AccountingFactService service;

  @Autowired private AccountingInvoiceIngestionService ingestion;

  @Autowired private AccountingSourceEvidenceService sourceEvidence;

  @Autowired private AccountingBankImportService bankImport;

  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean(name = "currencyRateService")
  private CurrencyRateService currencyConversion;

  @BeforeEach
  void configureCapturedFxGoldens() {
    stubFx(LocalDate.of(2026, 1, 30), "32171.23");
    stubFx(LocalDate.of(2026, 2, 27), "32249.12");
    stubFx(LocalDate.of(2026, 3, 30), "32706.52");
    stubFx(LocalDate.of(2026, 4, 29), "32481.25");
    stubFx(LocalDate.of(2026, 5, 29), "32317.08");
    stubFx(LocalDate.of(2026, 6, 29), "32750.80");
    stubFx(LocalDate.of(2026, 7, 30), "32908.87");
  }

  @Test
  void freezesKnownMonthlyComparisonMatrix() {
    Map<String, ExpectedMonth> expected = new LinkedHashMap<>();
    expected.put("2026-01", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-02", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-03", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-04", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-05", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-06", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put("2026-07", new ExpectedMonth("MATCH", "MATCH", "MATCH", "MATCH", "MATCH"));
    expected.put(
        "2026-08",
        new ExpectedMonth("MATCH", "NO_GOLDEN", "NO_GOLDEN", "NO_GOLDEN", "NO_FX_SOURCE"));

    expected.forEach(
        (month, monthExpected) -> {
          AccountingMonthSnapshot snapshot = service.snapshot(LocalDate.parse(month + "-01"));
          assertThat(status(snapshot, "REVENUE"))
              .as(month + " revenue")
              .isEqualTo(monthExpected.revenue());
          assertThat(status(snapshot, "RYCZALT"))
              .as(month + " ryczalt")
              .isEqualTo(monthExpected.ryczalt());
          assertThat(status(snapshot, "VAT")).as(month + " VAT").isEqualTo(monthExpected.vat());
          assertThat(status(snapshot, "ZUS")).as(month + " ZUS").isEqualTo(monthExpected.zus());
          assertThat(status(snapshot, "FX")).as(month + " FX").isEqualTo(monthExpected.fx());
        });
  }

  @Test
  void freezesCapturedDocumentLevelPurchaseVatTotals() {
    Map<String, String> expectedInputVat =
        Map.ofEntries(
            Map.entry("2026-01", "93.54"),
            Map.entry("2026-02", "100.55"),
            Map.entry("2026-03", "238.38"),
            Map.entry("2026-04", "120.20"),
            Map.entry("2026-05", "207.42"),
            Map.entry("2026-06", "196.10"),
            Map.entry("2026-07", "145.99"),
            Map.entry("2026-08", "0.00"));

    expectedInputVat.forEach(
        (month, expected) -> {
          AccountingMonthSnapshot snapshot = service.snapshot(LocalDate.parse(month + "-01"));
          assertThat(snapshot.vat().deductibleInputVat())
              .as(month + " document-level deductible purchase VAT")
              .isEqualByComparingTo(expected);
        });
  }

  @Test
  void appliesJpkIndependentWholeZlotyRoundingToMarchVat() {
    AccountingMonthSnapshot march = service.snapshot(LocalDate.of(2026, 3, 1));
    ComparisonRow vat = comparison(march, "VAT");

    assertThat(march.vat().deductibleInputVat()).isEqualByComparingTo("238.38");
    assertThat(vat.calculated()).isEqualByComparingTo("7251");
    assertThat(vat.expected()).isEqualByComparingTo("7251");
    assertThat(vat.difference()).isEqualByComparingTo("0");
    assertThat(vat.status()).isEqualTo("MATCH");
  }

  @Test
  void calculatesSeptemberFromPersistedNormalizedFactsWithoutMonthGolden() {
    LocalDate september = LocalDate.of(2026, 9, 1);
    long sourceId =
        sourceEvidence.receiveKsef("E2E-KSEF-SEPTEMBER", september.plusDays(10), "xml".getBytes());
    ingestion.ingest(
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            september,
            "SALES_INVOICE",
            september.plusDays(10),
            september.plusDays(10),
            "E2E-SEPTEMBER-SALE",
            "E2E CUSTOMER",
            "SERVICE",
            "PLN",
            new BigDecimal("1000.00"),
            new BigDecimal("230.00"),
            new BigDecimal("1230.00"),
            BigDecimal.ZERO,
            "E2E_TEST",
            "September source evidence",
            Long.toString(sourceId)));
    ingestion.ingest(
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            september,
            "PURCHASE_INVOICE",
            september.plusDays(11),
            null,
            "E2E-SEPTEMBER-PURCHASE",
            "E2E SUPPLIER",
            "ACCOUNTING_SERVICE",
            "PLN",
            new BigDecimal("100.00"),
            new BigDecimal("23.00"),
            new BigDecimal("123.00"),
            BigDecimal.ONE,
            "E2E_TEST",
            "September source evidence",
            Long.toString(sourceId)));
    sourceEvidence.status(sourceId, AccountingSourceStatus.IMPORTED, null);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?)",
        september,
        "HEALTH_CONTRIBUTION_PAID",
        new BigDecimal("100.00"),
        "E2E_TEST");
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?)",
        september,
        "JDG_COMPULSORY_SOCIAL_ZUS",
        new BigDecimal("200.00"),
        "E2E_TEST");

    assertThat(service.availablePeriods()).contains(september);
    AccountingMonthSnapshot snapshot = service.snapshot(september);
    assertThat(snapshot.calculationMode()).isEqualTo(AccountingCalculationMode.CURRENT_CALCULATION);
    assertThat(snapshot.comparisons()).isEmpty();
    assertThat(snapshot.readiness()).isEqualTo(AccountingReadiness.READY);
    assertThat(snapshot.totalBookedRevenuePln()).isEqualByComparingTo("1000.00");
  }

  @Test
  void normalizedAccountingRowsRejectUnknownSourceProvenance() {
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    INSERT INTO investory.accounting_poc_invoice
                        (tax_period, issue_date, sale_date, reference, customer_alias, invoice_kind,
                         currency, net_amount, vat_amount, gross_amount, expected_receivable,
                         booked_net_pln, ryczalt_rate, note, source_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 1),
                    LocalDate.of(2026, 9, 1),
                    "E2E-INVALID-SOURCE",
                    "CUSTOMER",
                    "DOMESTIC_SERVICE",
                    "PLN",
                    new BigDecimal("1"),
                    new BigDecimal("0.23"),
                    new BigDecimal("1.23"),
                    new BigDecimal("1.23"),
                    new BigDecimal("1"),
                    new BigDecimal("0.12"),
                    "E2E",
                    999999999L))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void bankImportReconcilesReceiptWithoutChangingAccountingAmount() {
    LocalDate september = LocalDate.of(2026, 9, 1);
    long sourceId =
        sourceEvidence.receiveKsef(
            "E2E-BANK-INVOICE", september.plusDays(10), "invoice".getBytes());
    ingestion.ingest(
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            september,
            "SALES_INVOICE",
            september.plusDays(10),
            september.plusDays(10),
            "E2E-BANK-INVOICE-REF",
            "E2E BANK CUSTOMER",
            "SERVICE",
            "PLN",
            new BigDecimal("1000.00"),
            new BigDecimal("230.00"),
            new BigDecimal("1230.00"),
            BigDecimal.ZERO,
            "E2E_TEST",
            "Bank reconciliation invoice",
            Long.toString(sourceId)));
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?)",
        september,
        "HEALTH_CONTRIBUTION_PAID",
        new BigDecimal("100.00"),
        "E2E_BANK_TEST");
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?)",
        september,
        "JDG_COMPULSORY_SOCIAL_ZUS",
        new BigDecimal("200.00"),
        "E2E_BANK_TEST");
    bankImport.importFile(
        "e2e-bank.csv",
        "text/csv",
        ("booking_date;related_period;reference;counterparty;currency;amount;note\n"
                + "2026-09-20;2026-09-01;E2E-BANK-INVOICE-REF;E2E BANK CUSTOMER;PLN;1230.00;customer receipt")
            .getBytes(),
        september);

    AccountingMonthSnapshot snapshot = service.snapshot(september);
    assertThat(snapshot.ryczalt().revenueBeforeDeductions()).isEqualByComparingTo("1000.00");
    assertThat(snapshot.reconciliations())
        .anySatisfy(
            row -> {
              assertThat(row.reference()).isEqualTo("E2E-BANK-INVOICE-REF");
              assertThat(row.status()).isEqualTo("MATCHED");
              assertThat(row.matchedAmount()).isEqualByComparingTo("1230.00");
            });
  }

  @org.junit.jupiter.api.AfterEach
  void removeOperationalFixture() {
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_invoice WHERE reference IN ('E2E-SEPTEMBER-SALE', 'E2E-INVALID-SOURCE')");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_expense_invoice WHERE reference = 'E2E-SEPTEMBER-PURCHASE'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_bank_transaction WHERE reference = 'E2E-BANK-INVOICE-REF'");
    jdbcTemplate.update("DELETE FROM investory.accounting_poc_tax_input WHERE note = 'E2E_TEST'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_source_evidence WHERE external_reference = 'E2E-KSEF-SEPTEMBER'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_invoice WHERE reference = 'E2E-BANK-INVOICE-REF'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_poc_tax_input WHERE note = 'E2E_BANK_TEST'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_source_evidence WHERE original_filename = 'e2e-bank.csv'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_source_evidence WHERE external_reference = 'E2E-BANK-INVOICE'");
  }

  private void stubFx(LocalDate rateDate, String pln) {
    when(currencyConversion.convertToBaseCurrency(
            new BigDecimal("7636.0000"), CurrencyType.PLN, CurrencyType.EUR, rateDate))
        .thenReturn(new BigDecimal(pln));
  }

  private String status(AccountingMonthSnapshot snapshot, String area) {
    return comparison(snapshot, area).status();
  }

  private ComparisonRow comparison(AccountingMonthSnapshot snapshot, String area) {
    return snapshot.comparisons().stream()
        .filter(row -> area.equals(row.area()))
        .findFirst()
        .orElseThrow();
  }

  private record ExpectedMonth(String revenue, String ryczalt, String vat, String zus, String fx) {}
}
