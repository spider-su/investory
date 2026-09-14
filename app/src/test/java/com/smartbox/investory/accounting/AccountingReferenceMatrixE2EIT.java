package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.service.AccountingFactService;
import com.smartbox.investory.accounting.service.AccountingInvoiceIngestionService.ReviewedInvoice;
import com.smartbox.investory.accounting.service.AccountingSourceEvidenceService;
import com.smartbox.investory.accounting.staging.AccountingBankStagingImportService;
import com.smartbox.investory.accounting.staging.AccountingStagingAcquisitionService;
import com.smartbox.investory.accounting.staging.AccountingStagingPromotionService;
import com.smartbox.investory.accounting.staging.AccountingStagingReconciliationService;
import com.smartbox.investory.investment.valuation.fx.CurrencyRateService;
import com.smartbox.investory.shared.currency.CurrencyType;
import com.smartbox.investory.testsupport.WorkerDatabase;
import com.smartbox.investory.testsupport.accounting.AccountingDatabase;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real acquisition-to-calculation regression against the immutable Jan-Aug reference oracle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test-fast")
class AccountingReferenceMatrixE2EIT {
  private static final WorkerDatabase DATABASE =
      AccountingDatabase.scopedReferenceDatabase("matrix_e2e");
  private static final long PROFILE_ID = 1L;

  @Autowired private JdbcTemplate jdbc;
  @Autowired private AccountingSourceEvidenceService sources;
  @Autowired private AccountingStagingAcquisitionService staging;
  @Autowired private AccountingBankStagingImportService bankImport;
  @Autowired private AccountingStagingPromotionService promotion;
  @Autowired private AccountingStagingReconciliationService reconciliation;
  @Autowired private AccountingFactService facts;

  @MockitoBean(name = "currencyRateService")
  private CurrencyRateService currency;

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

  @Test
  void importsPromotesAndComparesEveryJanToAugustMonthAtEachAccountingLayer() {
    prepareNonDocumentCalculationInputs();
    var referenceDocuments = referenceDocuments();
    stubForeignExchange(referenceDocuments);

    referenceDocuments.values().forEach(this::acquireAndStageInvoice);
    bankImport.stageFile(
        PROFILE_ID, "jan-aug-2026-bank.csv", "text/csv", bankCsv(), LocalDate.of(2026, 1, 1));

    for (int month = 1; month <= 8; month++) {
      LocalDate period = LocalDate.of(2026, month, 1);
      var summary = reconciliation.reconcile(PROFILE_ID, period);
      assertThat(summary.blockingCount()).as("%s staging blocking rows", period).isZero();
      promotion.promoteNew(PROFILE_ID, period);
    }
    SoftAssertions softly = new SoftAssertions();
    for (int month = 1; month <= 8; month++) {
      LocalDate period = LocalDate.of(2026, month, 1);
      compareMonth(period, softly);
    }
    softly.assertAll();
  }

  @Test
  void referenceOracleExercisesEveryMonthlyZusAndDateBranch() {
    var rows =
        jdbc.query(
            "SELECT case_key, tax_period, has_uop, voluntary_sickness, ytd_revenue, paid_social, "
                + "expected_health_band, expected_social, expected_deductible_social, expected_health, "
                + "correction_sale_date, correction_issue_date, expected_correction_period, "
                + "foreign_document_date, expected_fx_rate_date "
                + "FROM investory.accounting_reference_zus_branch ORDER BY tax_period, case_key",
            (rs, rowNum) -> ReferenceZusBranch.from(rs));

    assertThat(rows).hasSize(16);
    var byMonth = rows.stream().collect(Collectors.groupingBy(ReferenceZusBranch::taxPeriod));
    assertThat(byMonth).hasSize(8);
    for (int month = 1; month <= 8; month++) {
      LocalDate period = LocalDate.of(2026, month, 1);
      var monthly = byMonth.get(period);
      assertThat(monthly).as("reference rows for %s", period).hasSize(2);
      assertThat(monthly.stream().map(ReferenceZusBranch::caseKey).collect(Collectors.toSet()))
          .as("insurance branches for %s", period)
          .containsExactlyInAnyOrder("UOP", "JDG_SICKNESS");
      monthly.forEach(this::assertReferenceBranch);
    }

    assertThat(rows.stream().map(row -> row.expectedHealthBand()).collect(Collectors.toSet()))
        .containsExactlyInAnyOrder("LOW", "MEDIUM", "HIGH");
    assertThat(
            rows.stream()
                .map(ReferenceZusBranch::paidSocial)
                .map(value -> value.stripTrailingZeros().toPlainString())
                .distinct())
        .containsExactlyInAnyOrder("0", "1649.82");
  }

  private void assertReferenceBranch(ReferenceZusBranch row) {
    var band = ZusRules2026.healthBandAfterPaidSocial(row.ytdRevenue(), row.paidSocial());
    assertThat(band.name())
        .as("health band %s/%s", row.caseKey(), row.taxPeriod())
        .isEqualTo(row.expectedHealthBand());
    var result =
        new ZusCalculator()
            .calculate(
                new ZusCalculator.Input(
                    true,
                    row.hasUop(),
                    "JDG",
                    row.voluntarySickness(),
                    row.ytdRevenue(),
                    null,
                    band));
    assertThat(result.socialContribution()).isEqualByComparingTo(row.expectedSocial());
    assertThat(result.deductibleSocialContribution())
        .isEqualByComparingTo(row.expectedDeductibleSocial());
    assertThat(result.healthContribution()).isEqualByComparingTo(row.expectedHealth());
    assertThat(
            AccountingDateRules.accountingPeriod(
                row.correctionSaleDate(), row.correctionIssueDate(), null, true))
        .isEqualTo(row.expectedCorrectionPeriod());
    assertThat(AccountingDateRules.priorBusinessDay(row.foreignDocumentDate()))
        .isEqualTo(row.expectedFxRateDate());
  }

  private void prepareNonDocumentCalculationInputs() {
    jdbc.update(
        "INSERT INTO investory.accounting_poc_tax_input (profile_id, tax_period, input_type, amount, note) "
            + "SELECT profile_id, tax_period, input_type, amount, 'REFERENCE_E2E_INPUT' FROM investory.accounting_reference_tax_input WHERE profile_id=?",
        PROFILE_ID);
    jdbc.update(
        "INSERT INTO investory.accounting_poc_obligation (profile_id, tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, note) "
            + "SELECT profile_id, tax_period, obligation_type, due_date, expected_amount, paid_amount, payment_date, status, 'REFERENCE_E2E_INPUT' FROM investory.accounting_reference_obligation WHERE profile_id=?",
        PROFILE_ID);
  }

  private void acquireAndStageInvoice(InvoiceInput row) {
    long sourceId =
        sources.receiveKsef(
            row.reference(), row.issueDate(), row.reference().getBytes(StandardCharsets.UTF_8));
    staging.stageInvoice(
        PROFILE_ID, row.reviewedInvoice(Long.toString(sourceId)), row.vatTreatment());
    if (row.correctionNet() != null && row.correctionNet().signum() != 0) {
      long correctionSource =
          sources.receiveKsef(
              row.reference() + "-CORRECTION",
              row.period().plusMonths(1).withDayOfMonth(1),
              (row.reference() + "-CORRECTION").getBytes(StandardCharsets.UTF_8));
      staging.stageInvoice(
          PROFILE_ID, row.correctionInvoice(Long.toString(correctionSource)), "DOMESTIC_VAT");
    }
  }

  private void compareMonth(LocalDate period, SoftAssertions softly) {
    var expected =
        jdbc.queryForObject(
            "SELECT revenue + COALESCE((SELECT SUM(i2.correction_net_amount) FROM investory.accounting_reference_invoice i2 WHERE i2.profile_id=? AND i2.tax_period=?), 0), expenses, "
                + "output_vat + COALESCE((SELECT SUM(i2.correction_vat_amount) FROM investory.accounting_reference_invoice i2 WHERE i2.profile_id=? AND i2.tax_period=?), 0), deductible_input_vat, "
                + "output_vat + COALESCE((SELECT SUM(i2.correction_vat_amount) FROM investory.accounting_reference_invoice i2 WHERE i2.profile_id=? AND i2.tax_period=?), 0) - deductible_input_vat, "
                + "(SELECT COALESCE(SUM(o2.expected_amount), 0) FROM investory.accounting_reference_obligation o2 WHERE o2.profile_id=? AND o2.tax_period=? AND o2.obligation_type='RYCZALT'), zus, "
                + "document_count + CASE WHEN EXISTS (SELECT 1 FROM investory.accounting_reference_invoice i2 WHERE i2.profile_id=? AND i2.tax_period=? AND COALESCE(i2.correction_net_amount, 0) <> 0) THEN 1 ELSE 0 END, bank_count, "
                + "EXISTS (SELECT 1 FROM investory.accounting_reference_obligation o WHERE o.profile_id=? AND o.tax_period=? AND o.obligation_type='VAT'), "
                + "EXISTS (SELECT 1 FROM investory.accounting_reference_obligation o WHERE o.profile_id=? AND o.tax_period=? AND o.obligation_type='RYCZALT'), "
                + "EXISTS (SELECT 1 FROM investory.accounting_reference_obligation o WHERE o.profile_id=? AND o.tax_period=? AND o.obligation_type='ZUS') "
                + "FROM investory.accounting_reference_month WHERE profile_id=? AND tax_period=?",
            (rs, rowNum) ->
                new ExpectedMonth(
                    money(rs.getBigDecimal(1)),
                    money(rs.getBigDecimal(2)),
                    money(rs.getBigDecimal(3)),
                    money(rs.getBigDecimal(4)),
                    tax(rs.getBigDecimal(3)).subtract(tax(rs.getBigDecimal(4))),
                    tax(rs.getBigDecimal(6)),
                    money(rs.getBigDecimal(7)),
                    rs.getInt(8),
                    rs.getInt(9),
                    rs.getBoolean(10),
                    rs.getBoolean(11),
                    rs.getBoolean(12)),
            PROFILE_ID,
            period.minusMonths(1),
            PROFILE_ID,
            period.minusMonths(1),
            PROFILE_ID,
            period.minusMonths(1),
            PROFILE_ID,
            period,
            PROFILE_ID,
            period.minusMonths(1),
            PROFILE_ID,
            period,
            PROFILE_ID,
            period,
            PROFILE_ID,
            period,
            PROFILE_ID,
            period);
    var actual = facts.snapshot(PROFILE_ID, period);

    softly
        .assertThat(actual.invoices().size() + actual.expenses().size())
        .as("%s documents", period)
        .isEqualTo(expected.documents());
    softly
        .assertThat(actual.bankTransactions())
        .as("%s bank rows", period)
        .hasSize(expected.bankCount());
    softly
        .assertThat(actual.totalBookedRevenuePln())
        .as("%s revenue", period)
        .isEqualByComparingTo(expected.revenue());
    softly
        .assertThat(
            actual.expenses().stream()
                .map(AccountingMonthSnapshot.ExpenseRow::netAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .as("%s expenses", period)
        .isEqualByComparingTo(expected.expenses());
    if (expected.vatGolden()) {
      softly
          .assertThat(actual.vat().outputVatAfterSalesCorrection())
          .as("%s output VAT", period)
          .isEqualByComparingTo(expected.outputVat());
      softly
          .assertThat(actual.vat().deductibleInputVat())
          .as("%s input VAT", period)
          .isEqualByComparingTo(expected.inputVat());
      softly
          .assertThat(actual.vat().calculatedVat())
          .as("%s VAT payable", period)
          .isEqualByComparingTo(expected.vatPayable());
    }
    if (expected.ryczaltGolden()) {
      softly
          .assertThat(actual.ryczalt().calculatedTax())
          .as("%s ryczałt", period)
          .isEqualByComparingTo(expected.ryczalt());
    }
    if (expected.zusGolden()) {
      softly
          .assertThat(actual.zus().totalZus())
          .as("%s ZUS", period)
          .isEqualByComparingTo(expected.zus());
    }
    softly
        .assertThat(actual.comparisons())
        .filteredOn(row -> !"FX".equals(row.area()))
        .allMatch(
            row -> "MATCH".equals(row.status()) || "NO_GOLDEN".equals(row.status()),
            actual.comparisons().toString());
  }

  private Map<String, InvoiceInput> referenceDocuments() {
    return jdbc
        .query(
            "SELECT tax_period, issue_date, sale_date, reference, counterparty_alias, invoice_kind, currency, net_amount, vat_amount, gross_amount, correction_net_amount, correction_vat_amount, correction_gross_amount, "
                + "NULL::numeric AS vat_deduction_ratio, counterparty_tax_identifier, counterparty_country, ksef_number, booked_net_pln FROM investory.accounting_reference_invoice WHERE profile_id=? "
                + "UNION ALL SELECT tax_period, invoice_date, NULL::date, reference, supplier_alias, 'PURCHASE_INVOICE', currency, net_amount, vat_amount, gross_amount, NULL::numeric, NULL::numeric, NULL::numeric, vat_deduction_ratio, counterparty_tax_identifier, counterparty_country, ksef_number, NULL::numeric FROM investory.accounting_reference_expense_invoice WHERE profile_id=? ORDER BY 1, 4",
            (rs, rowNum) -> InvoiceInput.invoice(rs),
            PROFILE_ID,
            PROFILE_ID)
        .stream()
        .collect(Collectors.toMap(InvoiceInput::reference, Function.identity()));
  }

  private byte[] bankCsv() {
    var csv =
        new StringBuilder(
            "booking_date;related_period;reference;counterparty;currency;amount;note\n");
    jdbc.query(
        "SELECT booking_date, related_period, reference, counterparty_alias, currency, amount, note FROM investory.accounting_reference_bank_transaction WHERE profile_id=? ORDER BY booking_date, id",
        (rs, rowNum) -> {
          csv.append(rs.getDate(1).toLocalDate())
              .append(';')
              .append(rs.getDate(2) == null ? "" : rs.getDate(2).toLocalDate())
              .append(';')
              .append(rs.getString(3) == null ? "" : rs.getString(3))
              .append(';')
              .append(csvCell(rs.getString(4)))
              .append(';')
              .append(rs.getString(5))
              .append(';')
              .append(rs.getBigDecimal(6))
              .append(';')
              .append(csvCell(rs.getString(7)))
              .append('\n');
          return null;
        },
        PROFILE_ID);
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private String csvCell(String value) {
    return value == null ? "" : value.replace(";", ",").replace("\n", " ").replace("\r", " ");
  }

  private BigDecimal money(BigDecimal value) {
    return value == null ? null : value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  private BigDecimal tax(BigDecimal value) {
    return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP);
  }

  private void stubForeignExchange(Map<String, InvoiceInput> invoices) {
    Map<FxInput, BigDecimal> bookedBySourceNet =
        invoices.values().stream()
            .filter(row -> !"PLN".equals(row.currency()))
            .collect(
                Collectors.toMap(
                    row ->
                        new FxInput(
                            row.net().stripTrailingZeros(),
                            row.saleDate() == null ? row.issueDate() : row.saleDate()),
                    InvoiceInput::bookedNetPln,
                    (left, right) -> left));
    when(currency.convertToBaseCurrency(
            any(BigDecimal.class),
            any(CurrencyType.class),
            any(CurrencyType.class),
            nullable(LocalDate.class)))
        .thenAnswer(
            invocation ->
                bookedBySourceNet.get(
                    new FxInput(
                        invocation.getArgument(0, BigDecimal.class).stripTrailingZeros(),
                        invocation.getArgument(3, LocalDate.class))));
  }

  private record FxInput(BigDecimal sourceNet, LocalDate rateDate) {}

  private record ReferenceZusBranch(
      String caseKey,
      LocalDate taxPeriod,
      boolean hasUop,
      boolean voluntarySickness,
      BigDecimal ytdRevenue,
      BigDecimal paidSocial,
      String expectedHealthBand,
      BigDecimal expectedSocial,
      BigDecimal expectedDeductibleSocial,
      BigDecimal expectedHealth,
      LocalDate correctionSaleDate,
      LocalDate correctionIssueDate,
      LocalDate expectedCorrectionPeriod,
      LocalDate foreignDocumentDate,
      LocalDate expectedFxRateDate) {
    static ReferenceZusBranch from(java.sql.ResultSet rs) throws java.sql.SQLException {
      return new ReferenceZusBranch(
          rs.getString(1),
          rs.getDate(2).toLocalDate(),
          rs.getBoolean(3),
          rs.getBoolean(4),
          rs.getBigDecimal(5),
          rs.getBigDecimal(6),
          rs.getString(7),
          rs.getBigDecimal(8),
          rs.getBigDecimal(9),
          rs.getBigDecimal(10),
          rs.getDate(11).toLocalDate(),
          rs.getDate(12).toLocalDate(),
          rs.getDate(13).toLocalDate(),
          rs.getDate(14).toLocalDate(),
          rs.getDate(15).toLocalDate());
    }
  }

  private record ExpectedMonth(
      BigDecimal revenue,
      BigDecimal expenses,
      BigDecimal outputVat,
      BigDecimal inputVat,
      BigDecimal vatPayable,
      BigDecimal ryczalt,
      BigDecimal zus,
      int documents,
      int bankCount,
      boolean vatGolden,
      boolean ryczaltGolden,
      boolean zusGolden) {}

  private record InvoiceInput(
      LocalDate period,
      LocalDate issueDate,
      LocalDate saleDate,
      String reference,
      String counterparty,
      String kind,
      String currency,
      BigDecimal net,
      BigDecimal vat,
      BigDecimal gross,
      BigDecimal correctionNet,
      BigDecimal correctionVat,
      BigDecimal correctionGross,
      BigDecimal deduction,
      String taxId,
      String country,
      String ksef,
      BigDecimal bookedNetPln) {
    static InvoiceInput invoice(java.sql.ResultSet rs) throws java.sql.SQLException {
      return new InvoiceInput(
          rs.getDate(1).toLocalDate(),
          date(rs, 2),
          date(rs, 3),
          rs.getString(4),
          rs.getString(5),
          rs.getString(6),
          rs.getString(7),
          rs.getBigDecimal(8),
          rs.getBigDecimal(9),
          rs.getBigDecimal(10),
          rs.getBigDecimal(11),
          rs.getBigDecimal(12),
          rs.getBigDecimal(13),
          rs.getBigDecimal(14),
          rs.getString(15),
          rs.getString(16),
          rs.getString(17),
          rs.getBigDecimal(18));
    }

    ReviewedInvoice reviewedInvoice(String sourceId) {
      String type =
          kind.equals("CREDIT_NOTE")
              ? "CREDIT_NOTE"
              : (kind.contains("SERVICE") ? "SALES_INVOICE" : kind);
      return new ReviewedInvoice(
          period,
          type,
          issueDate,
          saleDate,
          reference,
          counterparty,
          "SERVICE",
          currency,
          type.equals("CREDIT_NOTE") ? net.abs() : net,
          type.equals("CREDIT_NOTE") ? vat.abs() : vat,
          type.equals("CREDIT_NOTE") ? gross.abs() : gross,
          deduction,
          "REFERENCE_E2E_INPUT",
          "sanitized reference fixture",
          sourceId,
          taxId,
          country,
          ksef,
          null,
          null);
    }

    ReviewedInvoice correctionInvoice(String sourceId) {
      return new ReviewedInvoice(
          period.plusMonths(1).withDayOfMonth(1),
          "CREDIT_NOTE",
          period.plusMonths(1).withDayOfMonth(2),
          period.plusMonths(1).withDayOfMonth(1),
          reference + "-CORRECTION",
          counterparty,
          "SERVICE",
          currency,
          correctionNet.abs(),
          correctionVat.abs(),
          correctionGross.abs(),
          deduction,
          "REFERENCE_E2E_INPUT",
          "sanitized correction fixture",
          sourceId,
          taxId,
          country,
          ksef,
          null,
          null);
    }

    String vatTreatment() {
      if (kind.equals("PURCHASE_INVOICE")) return "DOMESTIC_PURCHASE";
      if (kind.equals("CREDIT_NOTE") || kind.contains("DOMESTIC")) return "DOMESTIC_VAT";
      return "EU_B2B_REVERSE_CHARGE";
    }

    static LocalDate date(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
      return rs.getDate(index) == null ? null : rs.getDate(index).toLocalDate();
    }
  }
}
