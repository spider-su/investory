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
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAccountingMonthCalculatorTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);

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
  void unsupportedRateDoesNotUseFirstInvoiceRate() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    List<InvoiceRow> invoices =
        List.of(
            invoice("PLN-1", "PLN", "100.00", "0.12"), invoice("PLN-2", "PLN", "100.00", "0.08"));

    AccountingCalculationResult result =
        calculator(conversion).calculate(input(invoices, List.of()));

    assertThat(result.complete()).isFalse();
    assertThat(result.issues())
        .extracting(AccountingIssue::type)
        .contains("UNSUPPORTED_RYCZALT_RATE");
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
