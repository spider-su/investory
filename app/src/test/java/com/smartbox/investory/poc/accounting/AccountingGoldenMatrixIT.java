package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.ComparisonRow;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.accounting.AccountingDatabaseTest;
import java.math.BigDecimal;
import java.time.Instant;
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

  @Autowired private AccountingFilingService filingService;

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
            Long.toString(sourceId),
            "PL1234567890",
            "PL",
            "E2E-KSEF-SALE",
            new AccountingFilingEvidence(AccountingFilingEvidence.Type.KSEF, "E2E-KSEF-SALE")));
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
            Long.toString(sourceId),
            "PL0987654321",
            "PL",
            "E2E-KSEF-PURCHASE",
            new AccountingFilingEvidence(AccountingFilingEvidence.Type.KSEF, "E2E-KSEF-PURCHASE")));
    sourceEvidence.status(sourceId, AccountingSourceStatus.IMPORTED, null);
    insertOperationalProfile(september);
    insertOperationalVatTransactions(september, "E2E-SEPTEMBER-SALE", "E2E-SEPTEMBER-PURCHASE");
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?) ON CONFLICT (tax_period, input_type) DO NOTHING",
        september,
        "HEALTH_CONTRIBUTION_PAID",
        new BigDecimal("100.00"),
        "E2E_TEST");
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?) ON CONFLICT (tax_period, input_type) DO NOTHING",
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
  void completeOperationalMonthConfirmsAndProjectsJpkAndPayments() {
    LocalDate july = LocalDate.of(2026, 10, 1);
    long sourceId =
        sourceEvidence.receiveKsef("E2E-FILING-SEPTEMBER", july.plusDays(10), "filing".getBytes());
    ingestion.ingest(
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            july,
            "SALES_INVOICE",
            july.plusDays(10),
            july.plusDays(10),
            "E2E-FILING-SALE",
            "FILING CUSTOMER",
            "SERVICE",
            "PLN",
            new BigDecimal("1000.00"),
            new BigDecimal("230.00"),
            new BigDecimal("1230.00"),
            BigDecimal.ZERO,
            "E2E_TEST",
            "Complete filing month",
            Long.toString(sourceId),
            "PL1234567890",
            "PL",
            "M123456789-20261010-ABCDEF-123456-78",
            new AccountingFilingEvidence(
                AccountingFilingEvidence.Type.KSEF, "M123456789-20261010-ABCDEF-123456-78")));
    ingestion.ingest(
        new AccountingInvoiceIngestionService.ReviewedInvoice(
            july,
            "PURCHASE_INVOICE",
            july.plusDays(11),
            july.plusDays(11),
            "E2E-FILING-PURCHASE",
            "FILING SUPPLIER",
            "ACCOUNTING_SERVICE",
            "PLN",
            new BigDecimal("100.00"),
            new BigDecimal("23.00"),
            new BigDecimal("123.00"),
            BigDecimal.ONE,
            "E2E_TEST",
            "Complete filing month",
            Long.toString(sourceId),
            "PL0987654321",
            "PL",
            "M123456789-20261011-ABCDEF-123456-79",
            new AccountingFilingEvidence(
                AccountingFilingEvidence.Type.KSEF, "M123456789-20261011-ABCDEF-123456-79")));
    sourceEvidence.status(sourceId, AccountingSourceStatus.IMPORTED, null);
    insertOperationalProfile(july);
    insertOperationalVatTransactions(july, "E2E-FILING-SALE", "E2E-FILING-PURCHASE");
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?) ON CONFLICT (tax_period, input_type) DO NOTHING",
        july,
        "HEALTH_CONTRIBUTION_PAID",
        new BigDecimal("100.00"),
        "E2E_TEST");
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_poc_tax_input (tax_period, input_type, amount, note) VALUES (?, ?, ?, ?) ON CONFLICT (tax_period, input_type) DO NOTHING",
        july,
        "JDG_COMPULSORY_SOCIAL_ZUS",
        new BigDecimal("200.00"),
        "E2E_TEST");
    jdbcTemplate.update(
        "UPDATE investory.accounting_poc_profile SET nip = ?, first_name = ?, surname = ?, date_of_birth = ?, vat_payment_account = ?, ryczalt_payment_account = ?, zus_payment_account = ? WHERE id = 1",
        "1010000000",
        "Jan",
        "Testowy",
        LocalDate.of(1980, 1, 1),
        "PL00123456789012345678901234",
        "PL00123456789012345678901234",
        "PL00123456789012345678901234");

    filingService.confirm(july);
    AccountingFilingService.FilingResult filing = filingService.filing(july);

    assertThat(filing.ready()).isTrue();
    assertThat(new String(filingService.jpk(july))).contains("JPK_V7M (3)", "<P_51>207</P_51>");
    var instructions = filingService.paymentInstructions(july);
    assertThat(instructions)
        .extracting(AccountingPaymentInstruction::obligationType)
        .containsExactly("VAT", "RYCZALT", "ZUS");
    assertThat(instructions.getFirst().amount()).isEqualByComparingTo("207");
    for (var instruction : instructions) {
      jdbcTemplate.update(
          "INSERT INTO investory.accounting_poc_bank_transaction (booking_date, related_period, reference, counterparty_alias, currency, amount, transaction_type, scope, note) VALUES (?, ?, ?, 'TAX_AUTHORITY', 'PLN', ?, ?, 'BUSINESS', 'operational settlement payment')",
          instruction.dueDate().minusDays(1),
          july,
          "SETTLE-" + instruction.obligationType(),
          instruction.amount(),
          instruction.obligationType() + "_PAYMENT");
    }
    var snapshot = service.snapshot(july);
    var reconciliations =
        instructions.stream()
            .map(
                instruction ->
                    AccountingObligationReconciliation.compare(
                        instruction.obligationType(),
                        july,
                        instruction.amount(),
                        instruction.amount(),
                        instruction.amount(),
                        instruction.amount()))
            .toList();
    assertThat(reconciliations)
        .allMatch(row -> row.status() == AccountingObligationReconciliation.Status.SETTLED);
    filingService.recordAuthorityConfirmation(
        new AuthorityConfirmation(
            "TAX_OFFICE",
            "JPK_V7M",
            july,
            "UPO-JPK-OPERATIONAL",
            AuthorityConfirmation.ConfirmationType.JPK_UPO,
            AuthorityConfirmation.ConfirmationStatus.ACCEPTED,
            Instant.parse("2026-11-01T10:00:00Z"),
            null,
            "Imported UPO"));
    filingService.recordAuthorityConfirmation(
        new AuthorityConfirmation(
            "TAX_OFFICE",
            "VAT",
            july,
            "POST-VAT-OPERATIONAL",
            AuthorityConfirmation.ConfirmationType.TAX_ACCOUNT_POSTING,
            AuthorityConfirmation.ConfirmationStatus.POSTED,
            Instant.parse("2026-11-02T10:00:00Z"),
            null,
            "Imported posting"));
    filingService.recordAuthorityConfirmation(
        new AuthorityConfirmation(
            "TAX_OFFICE",
            "RYCZALT",
            july,
            "POST-PPE-OPERATIONAL",
            AuthorityConfirmation.ConfirmationType.TAX_ACCOUNT_POSTING,
            AuthorityConfirmation.ConfirmationStatus.POSTED,
            Instant.parse("2026-11-02T10:00:00Z"),
            null,
            "Imported PPE posting"));
    filingService.recordAuthorityConfirmation(
        new AuthorityConfirmation(
            "ZUS",
            "ZUS",
            july,
            "DRA-OPERATIONAL",
            AuthorityConfirmation.ConfirmationType.ZUS_DRA_ACCEPTANCE,
            AuthorityConfirmation.ConfirmationStatus.ACCEPTED,
            Instant.parse("2026-11-03T10:00:00Z"),
            null,
            "Imported DRA acceptance"));
    filingService.recordAuthorityConfirmation(
        new AuthorityConfirmation(
            "ZUS",
            "ZUS",
            july,
            "POST-ZUS-OPERATIONAL",
            AuthorityConfirmation.ConfirmationType.ZUS_ACCOUNT_POSTING,
            AuthorityConfirmation.ConfirmationStatus.POSTED,
            Instant.parse("2026-11-04T10:00:00Z"),
            null,
            "Imported ZUS posting"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM investory.accounting_authority_confirmation WHERE tax_period = ?",
                Integer.class,
                july))
        .isEqualTo(5);
    filingService.markFiled(july);
    filingService.markPaid(july);
    filingService.settle(july);
    filingService.lock(july);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT lifecycle_status FROM investory.accounting_poc_period_state WHERE tax_period = ?",
                String.class,
                july))
        .isEqualTo("LOCKED");
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
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_vat_transaction WHERE reference LIKE 'E2E-%'");
    jdbcTemplate.update(
        "DELETE FROM investory.employment_period WHERE profile_id = 1 AND date_from >= DATE '2026-09-01'");
    jdbcTemplate.update(
        "DELETE FROM investory.accounting_tax_profile_period WHERE profile_id = 1 AND valid_from >= DATE '2026-09-01'");
  }

  private void insertOperationalProfile(LocalDate period) {
    jdbcTemplate.update(
        "INSERT INTO investory.employment_period (profile_id, employment_type, date_from) VALUES (1, 'JDG', ?)"
            + " ON CONFLICT DO NOTHING",
        period);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_tax_profile_period (profile_id, valid_from, jdg_active, ryczalt_rate, vat_registered, vat_eu_registered, zus_regime, voluntary_sickness) VALUES (1, ?, true, 0.12, true, true, 'JDG', false)"
            + " ON CONFLICT DO NOTHING",
        period);
  }

  private void insertOperationalVatTransactions(
      LocalDate period, String saleReference, String purchaseReference) {
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_vat_transaction (tax_period, tax_date, source_document_id, reference, direction, treatment, counterparty_country, counterparty_tax_identifier, identifier_type, net_amount, vat_amount, deductible_vat, evidence) VALUES (?, ?, 'E2E-SOURCE', ?, 'SALE', 'DOMESTIC_VAT', 'PL', 'PL1234567890', 'NIP', 1000.00, 230.00, 0.00, 'OFF')",
        period,
        period.plusDays(10),
        saleReference);
    jdbcTemplate.update(
        "INSERT INTO investory.accounting_vat_transaction (tax_period, tax_date, source_document_id, reference, direction, treatment, counterparty_country, counterparty_tax_identifier, identifier_type, net_amount, vat_amount, deductible_vat, evidence) VALUES (?, ?, 'E2E-SOURCE', ?, 'PURCHASE', 'DOMESTIC_PURCHASE', 'PL', 'PL0987654321', 'NIP', 100.00, 23.00, 23.00, 'OFF')",
        period,
        period.plusDays(11),
        purchaseReference);
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
