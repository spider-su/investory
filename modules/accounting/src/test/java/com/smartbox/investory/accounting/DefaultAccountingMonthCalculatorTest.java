package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartbox.investory.accounting.AccountingMonthSnapshot.ExpenseRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.TaxInputRow;
import com.smartbox.investory.shared.currency.CurrencyConversion;
import com.smartbox.investory.shared.currency.CurrencyConversionUnavailableException;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAccountingMonthCalculatorTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);

  @Test
  void orchestratedTaxResultsAndObligationsComeFromAuthoritativeCalculators() {
    var base =
        input(
            List.of(
                invoice("PLN-12", "PLN", "1000.00", "0.12"),
                invoice("PLN-8", "PLN", "2000.00", "0.08")),
            List.of());
    var saleVat =
        new AccountingVatTransaction(
            PERIOD,
            "source-PLN-12",
            "PLN-12",
            AccountingVatTransaction.Direction.SALE,
            VatTreatment.DOMESTIC_VAT,
            "PL",
            "PL1234567890",
            "NIP",
            null,
            null,
            null,
            new BigDecimal("1000.00"),
            new BigDecimal("230.00"),
            BigDecimal.ZERO,
            "reviewed",
            new BigDecimal("23"));
    var calculationInput =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            base.periodContext(),
            List.of(saleVat),
            AccountingCalculationMode.HISTORICAL_RECONSTRUCTION);
    var result = calculator(mock(CurrencyConversion.class)).calculate(calculationInput);
    var directRyczalt =
        new RyczaltCalculator().calculate(calculationInput, result.fx(), new ArrayList<>());
    var directVat = new VatCalculator().calculate(calculationInput, new ArrayList<>());

    assertThat(result.ryczalt()).isEqualTo(directRyczalt);
    assertThat(result.vat()).isEqualTo(directVat);
    assertThat(result.ryczalt().calculatedTax())
        .isEqualByComparingTo(directRyczalt.calculatedTax());
    assertThat(result.vat().calculatedVat()).isEqualByComparingTo(directVat.calculatedVat());
    assertThat(result.calculatedObligations())
        .extracting(AccountingCalculationResult.CalculatedObligation::amount)
        .containsExactly(
            directRyczalt.calculatedTax(), directVat.calculatedVat(), result.zus().totalZus());
  }

  @Test
  void ryczaltDeductionAllocationIsIndependentOfInvoiceInputOrder() {
    var base =
        input(
            List.of(
                invoice("PLN-12", "PLN", "100.00", "0.12"),
                invoice("PLN-8", "PLN", "100.00", "0.08")),
            List.of());
    var context =
        new AccountingPeriodContext(
            PERIOD,
            true,
            false,
            "JDG",
            false,
            new BigDecimal("0.12"),
            true,
            false,
            new AccountingYearToDateContext(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(
                    new PaidContribution(
                        "SOCIAL", PERIOD, PERIOD, new BigDecimal("0.01"), null, 1L))),
            null);
    var forward =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            context,
            List.of(),
            AccountingCalculationMode.HISTORICAL_RECONSTRUCTION);
    var reversed =
        new AccountingCalculationInput(
            base.period(),
            List.of(base.invoices().get(1), base.invoices().get(0)),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            context,
            List.of(),
            AccountingCalculationMode.HISTORICAL_RECONSTRUCTION);

    var first = calculator(mock(CurrencyConversion.class)).calculate(forward);
    var second = calculator(mock(CurrencyConversion.class)).calculate(reversed);

    assertThat(first.ryczalt().taxableByRate()).isEqualTo(second.ryczalt().taxableByRate());
  }

  @Test
  void calculatesForeignRevenueThroughSharedConverterAndCreatesObligations() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    InvoiceRow invoice = invoice("USD-1", "USD", "100.00", "0.12");
    when(conversion.convertToBaseCurrency(
            new BigDecimal("100.00"), CurrencyType.PLN, CurrencyType.USD, PERIOD))
        .thenReturn(new BigDecimal("400.00"));

    AccountingCalculationResult result =
        calculator(conversion).calculate(input(List.of(invoice), List.of()));

    assertThat(result.revenue().totalPln()).isEqualByComparingTo("400.00");
    assertThat(result.fx().entries()).singleElement().extracting("currency").isEqualTo("USD");
    assertThat(result.calculatedObligations())
        .extracting(AccountingCalculationResult.CalculatedObligation::type)
        .containsExactly("RYCZALT", "VAT", "ZUS");
  }

  @Test
  void unavailableFxIsAnExplicitIncompleteCalculation() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    InvoiceRow invoice = invoice("USD-1", "USD", "100.00", "0.12");
    when(conversion.convertToBaseCurrency(
            new BigDecimal("100.00"), CurrencyType.PLN, CurrencyType.USD, PERIOD))
        .thenThrow(new CurrencyConversionUnavailableException("missing"));

    AccountingCalculationResult result =
        calculator(conversion).calculate(input(List.of(invoice), List.of()));

    assertThat(result.complete()).isFalse();
    assertThat(result.fx().unavailableReferences()).containsExactly("USD-1");
    assertThat(result.issues()).extracting(AccountingIssue::type).contains("MISSING_FX");
  }

  @Test
  void calculatesMixedRyczaltRatesPerRevenueBucket() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    List<InvoiceRow> invoices =
        List.of(
            invoice("PLN-1", "PLN", "100.00", "0.12"), invoice("PLN-2", "PLN", "100.00", "0.08"));

    AccountingCalculationResult result =
        calculator(conversion).calculate(input(invoices, List.of()));

    assertThat(result.complete()).isTrue();
    assertThat(result.ryczalt().revenueByRate())
        .containsEntry(new BigDecimal("0.12"), new BigDecimal("100.00"));
    assertThat(result.ryczalt().revenueByRate())
        .containsEntry(new BigDecimal("0.08"), new BigDecimal("100.00"));
    assertThat(result.ryczalt().calculatedTax()).isEqualByComparingTo("20");
  }

  @Test
  void usesEffectiveUopStateInsteadOfCompatibilityProfileFlag() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-1", "PLN", "1000.00", "0.12")), List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            new AccountingProfile(false),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD, true, true, "JDG", false, AccountingYearToDateContext.empty()));

    AccountingCalculationResult result =
        new DefaultAccountingMonthCalculator(conversion).calculate(effective);

    assertThat(result.zus().socialZus()).isZero();
    assertThat(result.zus().socialZusReasonCode()).isEqualTo("UOP_PRIMARY_INSURANCE");
  }

  @Test
  void onlyPaidContributionsAreUsedForRyczaltDeduction() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-1", "PLN", "1000.00", "0.12")), List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            List.of(
                new TaxInputRow(
                    "JDG_COMPULSORY_SOCIAL_ZUS", new BigDecimal("200.00"), "obligation")),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD,
                true,
                false,
                "JDG",
                false,
                new AccountingYearToDateContext(
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        new PaidContribution(
                            "SOCIAL", PERIOD, PERIOD, new BigDecimal("100.00"), null, 7L),
                        new PaidContribution(
                            "HEALTH", PERIOD, PERIOD, new BigDecimal("100.00"), null, 8L)))));

    AccountingCalculationResult result =
        new DefaultAccountingMonthCalculator(conversion).calculate(effective);

    assertThat(result.ryczalt().socialContributionDeduction()).isEqualByComparingTo("100.00");
    assertThat(result.ryczalt().healthDeduction()).isEqualByComparingTo("50.00");
    assertThat(result.ryczalt().taxableBase()).isEqualByComparingTo("850.00");
  }

  @Test
  void roundsRyczaltTaxableBaseBeforeApplyingTheRate() {
    AccountingCalculationResult result =
        calculator(mock(CurrencyConversion.class))
            .calculate(
                input(List.of(invoice("PLN-ROUNDING", "PLN", "1004.49", "0.12")), List.of()));

    assertThat(result.ryczalt().taxableBase()).isEqualByComparingTo("1004");
    assertThat(result.ryczalt().calculatedTax()).isEqualByComparingTo("120");
  }

  @Test
  void uopStopsZusSocialAccrualButDoesNotRemoveExplicitPaidSocialDeduction() {
    var base = input(List.of(invoice("PLN-UOP", "PLN", "1000.00", "0.12")), List.of());
    var effective =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            List.of(new TaxInputRow("SOCIAL_CONTRIBUTION_PAID", new BigDecimal("100.00"), "paid")),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD,
                true,
                true,
                "JDG",
                false,
                new BigDecimal("0.12"),
                true,
                true,
                AccountingYearToDateContext.empty(),
                null),
            List.of(),
            AccountingCalculationMode.HISTORICAL_RECONSTRUCTION);

    var result = calculator(mock(CurrencyConversion.class)).calculate(effective);

    assertThat(result.zus().socialZus()).isZero();
    assertThat(result.ryczalt().socialContributionDeduction()).isEqualByComparingTo("100.00");
    assertThat(result.ryczalt().taxableBase()).isEqualByComparingTo("900");
  }

  @Test
  void neverCreatesNegativeRyczaltTaxWhenPaidContributionsExceedRevenue() {
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-LOW", "PLN", "100.00", "0.12")), List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD,
                true,
                false,
                "JDG",
                false,
                new AccountingYearToDateContext(
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        new PaidContribution(
                            "SOCIAL", PERIOD, PERIOD, new BigDecimal("200.00"), null, 7L)))),
            List.of(),
            AccountingCalculationMode.HISTORICAL_RECONSTRUCTION);

    AccountingCalculationResult result =
        calculator(mock(CurrencyConversion.class)).calculate(effective);

    assertThat(result.ryczalt().taxableBase()).isZero();
    assertThat(result.ryczalt().calculatedTax()).isZero();
    assertThat(result.ryczalt().deductionCarryForward()).isEqualByComparingTo("100.00");
  }

  @Test
  void carriesUnusedDeductionToTheNextMonthWithinTheSameTaxYear() {
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-CARRY", "PLN", "50.00", "0.12")), List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD,
                true,
                false,
                "JDG",
                false,
                new AccountingYearToDateContext(
                    new BigDecimal("150.00"),
                    null,
                    null,
                    null,
                    List.of(
                        new PaidContribution(
                            "SOCIAL",
                            PERIOD.minusMonths(1),
                            PERIOD.minusMonths(1),
                            new BigDecimal("200.00"),
                            null,
                            7L)))));

    AccountingCalculationResult result =
        calculator(mock(CurrencyConversion.class)).calculate(effective);

    assertThat(result.ryczalt().deductionUsed()).isEqualByComparingTo("50.00");
    assertThat(result.ryczalt().deductionCarryForward()).isEqualByComparingTo("50.00");
    assertThat(result.ryczalt().taxableBase()).isZero();
  }

  @Test
  void allocatesOnlyUsedDeductionAcrossMixedRatesAndKeepsCanonicalTaxableBuckets() {
    AccountingCalculationInput base =
        input(
            List.of(
                invoice("PLN-12", "PLN", "100.00", "0.12"),
                invoice("PLN-8", "PLN", "100.00", "0.08")),
            List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            base.period(),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD,
                true,
                false,
                "JDG",
                false,
                new AccountingYearToDateContext(
                    new BigDecimal("200.00"),
                    null,
                    null,
                    null,
                    List.of(
                        new PaidContribution(
                            "SOCIAL", PERIOD, PERIOD, new BigDecimal("250.00"), null, 7L)))));

    AccountingCalculationResult result =
        calculator(mock(CurrencyConversion.class)).calculate(effective);

    assertThat(result.ryczalt().deductionUsed()).isEqualByComparingTo("200.00");
    assertThat(result.ryczalt().taxableByRate().values()).allMatch(value -> value.signum() >= 0);
    assertThat(result.ryczalt().taxableByRate().values())
        .allMatch(value -> value.compareTo(BigDecimal.ZERO) == 0);
    assertThat(result.ryczalt().taxableBase()).isZero();
    assertThat(result.ryczalt().deductionCarryForward()).isEqualByComparingTo("50.00");
  }

  @Test
  void deductsContributionInTheMonthItWasPaidNotItsContributionMonth() {
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-PAID-LATE", "PLN", "100.00", "0.12")), List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            base.period().plusMonths(1),
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                PERIOD.plusMonths(1),
                true,
                false,
                "JDG",
                false,
                new AccountingYearToDateContext(
                    new BigDecimal("100.00"),
                    null,
                    null,
                    null,
                    List.of(
                        new PaidContribution(
                            "SOCIAL",
                            PERIOD,
                            PERIOD.plusMonths(1),
                            new BigDecimal("100.00"),
                            null,
                            7L)))));

    AccountingCalculationResult result =
        calculator(mock(CurrencyConversion.class)).calculate(effective);

    assertThat(result.ryczalt().deductionUsed()).isEqualByComparingTo("100.00");
    assertThat(result.ryczalt().taxableBase()).isZero();
  }

  @Test
  void doesNotCarryPaidDeductionsAcrossTaxYears() {
    LocalDate january = LocalDate.of(2027, 1, 1);
    InvoiceRow invoice = invoice("PLN-YEAR", "PLN", "100.00", "0.12");
    AccountingCalculationInput base = input(List.of(invoice), List.of());
    AccountingCalculationInput effective =
        new AccountingCalculationInput(
            january,
            base.invoices(),
            base.expenses(),
            base.taxInputs(),
            base.profile(),
            base.adjustments(),
            new AccountingPeriodContext(
                january,
                true,
                false,
                "JDG",
                false,
                new AccountingYearToDateContext(
                    new BigDecimal("100.00"),
                    null,
                    null,
                    null,
                    List.of(
                        new PaidContribution(
                            "SOCIAL",
                            LocalDate.of(2026, 12, 1),
                            LocalDate.of(2026, 12, 31),
                            new BigDecimal("200.00"),
                            null,
                            7L)))));

    AccountingCalculationResult result =
        calculator(mock(CurrencyConversion.class)).calculate(effective);

    assertThat(result.ryczalt().deductionUsed()).isZero();
    assertThat(result.ryczalt().taxableBase()).isEqualByComparingTo("100");
    assertThat(result.ryczalt().calculatedTax()).isEqualByComparingTo("12");
  }

  @Test
  void explicitVatTreatmentControlsOutputInsteadOfCurrency() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    InvoiceRow invoice = invoice("EU-1", "PLN", "100.00", "0.12");
    AccountingVatTransaction transaction =
        new AccountingVatTransaction(
            PERIOD,
            "source",
            "EU-1",
            AccountingVatTransaction.Direction.SALE,
            VatTreatment.EU_B2B_REVERSE_CHARGE,
            "DE",
            "DE123",
            "VAT",
            "DE123",
            PERIOD,
            "VERIFIED",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "reviewed");

    AccountingCalculationInput base = input(List.of(invoice), List.of());
    AccountingCalculationResult result =
        new DefaultAccountingMonthCalculator(conversion)
            .calculate(
                new AccountingCalculationInput(
                    base.period(),
                    base.invoices(),
                    base.expenses(),
                    base.taxInputs(),
                    base.profile(),
                    base.adjustments(),
                    base.periodContext(),
                    List.of(transaction)));

    assertThat(result.vat().outputVat()).isZero();
  }

  @Test
  void currentCalculationDoesNotFallBackToLegacyZusRows() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-1", "PLN", "100.00", "0.12")), List.of());
    AccountingCalculationResult result =
        calculator(conversion)
            .calculate(
                new AccountingCalculationInput(
                    base.period(),
                    base.invoices(),
                    base.expenses(),
                    base.taxInputs(),
                    base.profile(),
                    base.adjustments(),
                    new AccountingPeriodContext(
                        PERIOD,
                        true,
                        false,
                        "JDG",
                        false,
                        new BigDecimal("0.12"),
                        true,
                        false,
                        AccountingYearToDateContext.empty(),
                        null),
                    List.of(),
                    AccountingCalculationMode.CURRENT_CALCULATION));

    assertThat(result.zus().socialZus()).isZero();
    assertThat(result.zus().healthZus()).isZero();
    assertThat(result.issues())
        .extracting(AccountingIssue::type)
        .contains("MISSING_ZUS_RULE_INPUT");
  }

  @Test
  void currentCalculationRequiresExplicitVatRows() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    AccountingCalculationInput base =
        input(List.of(invoice("PLN-1", "PLN", "100.00", "0.12")), List.of());
    AccountingCalculationResult result =
        calculator(conversion)
            .calculate(
                new AccountingCalculationInput(
                    base.period(),
                    base.invoices(),
                    base.expenses(),
                    base.taxInputs(),
                    base.profile(),
                    base.adjustments(),
                    new AccountingPeriodContext(
                        PERIOD,
                        true,
                        false,
                        "JDG",
                        false,
                        new BigDecimal("0.12"),
                        true,
                        false,
                        AccountingYearToDateContext.empty(),
                        new ZusCalculationInput(
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "MIN", "JDG")),
                    List.of(),
                    AccountingCalculationMode.CURRENT_CALCULATION));

    assertThat(result.issues())
        .extracting(AccountingIssue::type)
        .contains("MISSING_VAT_CLASSIFICATION");
  }

  @Test
  void currentCalculationRequiresVatTreatmentForEveryDocument() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    InvoiceRow first = invoice("PLN-1", "PLN", "100.00", "0.12");
    InvoiceRow second = invoice("PLN-2", "PLN", "200.00", "0.12");
    AccountingVatTransaction treatment =
        new AccountingVatTransaction(
            PERIOD,
            "source",
            first.reference(),
            AccountingVatTransaction.Direction.SALE,
            VatTreatment.DOMESTIC_VAT,
            "PL",
            "PL123",
            "NIP",
            null,
            null,
            null,
            first.netAmount(),
            first.vatAmount(),
            BigDecimal.ZERO,
            "reviewed");

    AccountingCalculationInput base = input(List.of(first, second), List.of());
    AccountingCalculationResult result =
        calculator(conversion)
            .calculate(
                new AccountingCalculationInput(
                    base.period(),
                    base.invoices(),
                    base.expenses(),
                    base.taxInputs(),
                    base.profile(),
                    base.adjustments(),
                    base.periodContext(),
                    List.of(treatment),
                    AccountingCalculationMode.CURRENT_CALCULATION));

    assertThat(result.issues())
        .anySatisfy(
            issue -> {
              assertThat(issue.type()).isEqualTo("MISSING_VAT_CLASSIFICATION");
              assertThat(issue.sourceReference()).isEqualTo(second.reference());
            });
  }

  private DefaultAccountingMonthCalculator calculator(CurrencyConversion conversion) {
    return new DefaultAccountingMonthCalculator(conversion);
  }

  private AccountingCalculationInput input(List<InvoiceRow> invoices, List<ExpenseRow> expenses) {
    return new AccountingCalculationInput(
        PERIOD,
        invoices,
        expenses,
        List.of(
            new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("100.00"), "test"),
            new TaxInputRow("JDG_COMPULSORY_SOCIAL_ZUS", new BigDecimal("200.00"), "test")),
        new AccountingProfile(false),
        AccountingCalculationInput.CalculationAdjustments.none());
  }

  private InvoiceRow invoice(String reference, String currency, String net, String rate) {
    BigDecimal amount = new BigDecimal(net);
    return new InvoiceRow(
        1,
        PERIOD,
        PERIOD,
        PERIOD,
        PERIOD,
        reference,
        "customer",
        "SALE",
        currency,
        amount,
        BigDecimal.ZERO,
        amount,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        amount,
        "PLN".equals(currency) ? amount : null,
        new BigDecimal(rate),
        "test");
  }
}
