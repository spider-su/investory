package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VatCalculatorTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 3, 1);

  @Test
  void calculatesPayableVatFromIndependentlyRoundedJpkComponents() {
    var result =
        calculate(
            List.of(
                transaction(
                    "SALE",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.DOMESTIC_VAT,
                    "10000",
                    "1234.49",
                    "0",
                    "23"),
                transaction(
                    "PURCHASE",
                    AccountingVatTransaction.Direction.PURCHASE,
                    VatTreatment.DOMESTIC_PURCHASE,
                    "5000",
                    "1000.51",
                    "1000.51",
                    "23")),
            List.of(),
            new ArrayList<>());

    // The March reference scenario freezes JPK's component-wise rounding (7,489 - 238 = 7,251).
    // Therefore 1,234.49 - 1,000.51 rounds to 1,234 - 1,001 = 233, not 234.
    assertThat(result.outputVat()).isEqualByComparingTo("1234.49");
    assertThat(result.deductibleInputVat()).isEqualByComparingTo("1000.51");
    assertThat(result.calculatedVat()).isEqualByComparingTo("233");
  }

  @Test
  void roundsVatComponentsAtTheWholeZlotyHalfUpBoundaries() {
    assertThat(payableFor("1234.49")).isEqualByComparingTo("1234");
    assertThat(payableFor("1234.50")).isEqualByComparingTo("1235");
    assertThat(payableFor("1234.51")).isEqualByComparingTo("1235");
  }

  @Test
  void payableIsZeroWhenOutputVatIsZero() {
    var result =
        calculate(
            List.of(
                transaction(
                    "EXEMPT",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.VAT_EXEMPT,
                    "100",
                    "0",
                    "0",
                    "0")),
            List.of(),
            new ArrayList<>());

    assertThat(result.calculatedVat()).isZero();
  }

  @Test
  void excessDeductibleVatRemainsVisibleWhilePayableIsFlooredAtZero() {
    var result =
        calculate(
            List.of(
                transaction(
                    "SALE",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.DOMESTIC_VAT,
                    "1000",
                    "50",
                    "0",
                    "5"),
                transaction(
                    "PURCHASE",
                    AccountingVatTransaction.Direction.PURCHASE,
                    VatTreatment.DOMESTIC_PURCHASE,
                    "1000",
                    "80",
                    "80",
                    "8")),
            List.of(),
            new ArrayList<>());

    assertThat(result.outputVat()).isEqualByComparingTo("50");
    assertThat(result.deductibleInputVat()).isEqualByComparingTo("80");
    assertThat(result.calculatedVat()).isZero();
    // The current obligation model does not represent a refund or a carry-forward balance.
  }

  @Test
  void computesVatFromReferencedClassifiedDocuments() {
    var issues = new ArrayList<AccountingIssue>();
    var result =
        calculate(
            List.of(
                transaction(
                    "SALE",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.DOMESTIC_VAT,
                    "100",
                    "23",
                    "0",
                    "23")),
            List.of(),
            issues);

    assertThat(issues).isEmpty();
    assertThat(result.calculatedVat()).isEqualByComparingTo("23");
  }

  @Test
  void reportsMissingClassificationForReferencedDocument() {
    var invoice = invoice("SALE-MISSING", "100", "23");
    var issues = new ArrayList<AccountingIssue>();

    calculate(
        List.of(
            transaction(
                "OTHER",
                AccountingVatTransaction.Direction.SALE,
                VatTreatment.DOMESTIC_VAT,
                "100",
                "23",
                "0",
                "23")),
        List.of(invoice),
        issues);

    assertThat(issues)
        .anySatisfy(
            issue -> {
              assertThat(issue.type()).isEqualTo("MISSING_VAT_CLASSIFICATION");
              assertThat(issue.sourceReference()).isEqualTo("SALE-MISSING");
            });
  }

  @Test
  void unreferencedDocumentCannotDisappearFromVatClassificationValidation() {
    var invoice = invoice(null, "100", "23");
    var issues = new ArrayList<AccountingIssue>();

    var result =
        calculate(
            List.of(
                transaction(
                    "OTHER",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.DOMESTIC_VAT,
                    "100",
                    "23",
                    "0",
                    "23")),
            List.of(invoice),
            issues);

    assertThat(issues)
        .anySatisfy(
            issue -> {
              assertThat(issue.type()).isEqualTo("MISSING_VAT_CLASSIFICATION");
              assertThat(issue.sourceReference()).isNull();
              assertThat(issue.message()).contains("reference is missing");
            });
    assertThat(result.calculatedVat()).isZero();
  }

  @Test
  void acceptsPositiveVatAndSameDirectionCreditNote() {
    var issues = new ArrayList<AccountingIssue>();
    calculate(
        List.of(
            transaction(
                "SALE",
                AccountingVatTransaction.Direction.SALE,
                VatTreatment.DOMESTIC_VAT,
                "100",
                "23",
                "0",
                "23"),
            transaction(
                "CREDIT",
                AccountingVatTransaction.Direction.SALE,
                VatTreatment.DOMESTIC_VAT,
                "-100",
                "-23",
                "0",
                "23")),
        List.of(),
        issues);

    assertThat(issues).noneMatch(issue -> "INVALID_VAT_SIGN".equals(issue.type()));
  }

  @Test
  void rejectsZeroVatOnNegativeNetForPositiveRateDomesticTreatment() {
    var issues = new ArrayList<AccountingIssue>();
    calculate(
        List.of(
            transaction(
                "BAD-CREDIT",
                AccountingVatTransaction.Direction.SALE,
                VatTreatment.DOMESTIC_VAT,
                "-100",
                "0",
                "0",
                "23")),
        List.of(),
        issues);

    assertThat(issues).anyMatch(issue -> "INVALID_VAT_SIGN".equals(issue.type()));
  }

  @Test
  void allowsZeroVatForExemptAndZeroRatedDocuments() {
    var issues = new ArrayList<AccountingIssue>();
    calculate(
        List.of(
            transaction(
                "EXEMPT-CREDIT",
                AccountingVatTransaction.Direction.SALE,
                VatTreatment.VAT_EXEMPT,
                "-100",
                "0",
                "0",
                null),
            transaction(
                "ZERO-RATED-CREDIT",
                AccountingVatTransaction.Direction.SALE,
                VatTreatment.DOMESTIC_VAT,
                "-100",
                "0",
                "0",
                "0")),
        List.of(),
        issues);

    assertThat(issues).noneMatch(issue -> "INVALID_VAT_SIGN".equals(issue.type()));
  }

  private BigDecimal payableFor(String vat) {
    return calculate(
            List.of(
                transaction(
                    "SALE",
                    AccountingVatTransaction.Direction.SALE,
                    VatTreatment.DOMESTIC_VAT,
                    "10000",
                    vat,
                    "0",
                    "23")),
            List.of(),
            new ArrayList<>())
        .calculatedVat();
  }

  private AccountingCalculationResult.VatCalculation calculate(
      List<AccountingVatTransaction> transactions,
      List<AccountingMonthSnapshot.InvoiceRow> invoices,
      List<AccountingIssue> issues) {
    var input =
        new AccountingCalculationInput(
            PERIOD,
            invoices,
            List.of(),
            List.of(),
            new AccountingProfile(false),
            AccountingCalculationInput.CalculationAdjustments.none(),
            AccountingPeriodContext.compatibility(PERIOD, new AccountingProfile(false)),
            transactions,
            AccountingCalculationMode.CURRENT_CALCULATION);
    return new VatCalculator().calculate(input, issues);
  }

  private AccountingMonthSnapshot.InvoiceRow invoice(String reference, String net, String vat) {
    BigDecimal netAmount = new BigDecimal(net);
    return new AccountingMonthSnapshot.InvoiceRow(
        1,
        PERIOD,
        PERIOD,
        PERIOD,
        PERIOD,
        reference,
        "buyer",
        "SALE",
        "PLN",
        netAmount,
        new BigDecimal(vat),
        netAmount.add(new BigDecimal(vat)),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        netAmount,
        netAmount,
        new BigDecimal("0.12"),
        "test");
  }

  private AccountingVatTransaction transaction(
      String reference,
      AccountingVatTransaction.Direction direction,
      VatTreatment treatment,
      String net,
      String vat,
      String deductible,
      String rate) {
    return new AccountingVatTransaction(
        PERIOD,
        "source-" + reference,
        reference,
        direction,
        treatment,
        "PL",
        "PL1234567890",
        "NIP",
        null,
        null,
        null,
        new BigDecimal(net),
        new BigDecimal(vat),
        new BigDecimal(deductible),
        "reviewed",
        rate == null ? null : new BigDecimal(rate));
  }
}
