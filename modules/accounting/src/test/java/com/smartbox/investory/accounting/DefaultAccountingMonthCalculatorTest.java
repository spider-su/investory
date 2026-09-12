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

    AccountingCalculationResult result = calculator(conversion).calculate(input(List.of(invoice), List.of()));

    assertThat(result.revenue().totalPln()).isEqualByComparingTo("400.00");
    assertThat(result.fx().entries()).singleElement().extracting("currency").isEqualTo("USD");
    assertThat(result.calculatedObligations()).extracting(AccountingCalculationResult.CalculatedObligation::type)
        .containsExactly("RYCZALT", "VAT", "ZUS");
  }

  @Test
  void unavailableFxIsAnExplicitIncompleteCalculation() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    InvoiceRow invoice = invoice("USD-1", "USD", "100.00", "0.12");
    when(conversion.convertToBaseCurrency(
            new BigDecimal("100.00"), CurrencyType.PLN, CurrencyType.USD, PERIOD))
        .thenThrow(new CurrencyConversionUnavailableException("missing"));

    AccountingCalculationResult result = calculator(conversion).calculate(input(List.of(invoice), List.of()));

    assertThat(result.complete()).isFalse();
    assertThat(result.fx().unavailableReferences()).containsExactly("USD-1");
    assertThat(result.issues()).extracting(AccountingIssue::type).contains("MISSING_FX");
  }

  @Test
  void unsupportedRateDoesNotUseFirstInvoiceRate() {
    CurrencyConversion conversion = mock(CurrencyConversion.class);
    List<InvoiceRow> invoices = List.of(invoice("PLN-1", "PLN", "100.00", "0.12"), invoice("PLN-2", "PLN", "100.00", "0.08"));

    AccountingCalculationResult result = calculator(conversion).calculate(input(invoices, List.of()));

    assertThat(result.complete()).isFalse();
    assertThat(result.issues()).extracting(AccountingIssue::type).contains("UNSUPPORTED_RYCZALT_RATE");
  }

  private DefaultAccountingMonthCalculator calculator(CurrencyConversion conversion) {
    return new DefaultAccountingMonthCalculator(conversion);
  }

  private AccountingCalculationInput input(List<InvoiceRow> invoices, List<ExpenseRow> expenses) {
    return new AccountingCalculationInput(PERIOD, invoices, expenses,
        List.of(new TaxInputRow("HEALTH_CONTRIBUTION_PAID", new BigDecimal("100.00"), "test"),
            new TaxInputRow("JDG_COMPULSORY_SOCIAL_ZUS", new BigDecimal("200.00"), "test")),
        new AccountingProfile(false), AccountingCalculationInput.CalculationAdjustments.none());
  }

  private InvoiceRow invoice(String reference, String currency, String net, String rate) {
    BigDecimal amount = new BigDecimal(net);
    return new InvoiceRow(1, PERIOD, PERIOD, PERIOD, PERIOD, reference, "customer", "SALE", currency,
        amount, BigDecimal.ZERO, amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, amount,
        "PLN".equals(currency) ? amount : null, new BigDecimal(rate), "test");
  }
}
