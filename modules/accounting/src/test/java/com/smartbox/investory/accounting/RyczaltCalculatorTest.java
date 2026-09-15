package com.smartbox.investory.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.accounting.AccountingCalculationResult.FxCalculation;
import com.smartbox.investory.accounting.AccountingMonthSnapshot.InvoiceRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RyczaltCalculatorTest {
  private static final LocalDate PERIOD = LocalDate.of(2026, 9, 1);

  @Test
  void calculatesTaxFromDocumentRateAndNetRevenue() {
    var profile = new AccountingProfile(false);
    var input =
        new AccountingCalculationInput(
            PERIOD,
            List.of(invoice("SALE-1", "10000.00", "0.12")),
            List.of(),
            List.of(),
            profile,
            AccountingCalculationInput.CalculationAdjustments.none());
    var fx = new FxCalculation(List.of(), BigDecimal.ZERO, List.of());

    var result = new RyczaltCalculator().calculate(input, fx, new ArrayList<>());

    assertThat(result.revenueBeforeDeductions()).isEqualByComparingTo("10000.00");
    assertThat(result.taxableByRate())
        .containsEntry(new BigDecimal("0.12"), new BigDecimal("10000.00"));
    assertThat(result.calculatedTax()).isEqualByComparingTo("1200");
  }

  private InvoiceRow invoice(String reference, String net, String rate) {
    BigDecimal amount = new BigDecimal(net);
    return new InvoiceRow(
        1,
        PERIOD,
        PERIOD,
        PERIOD,
        PERIOD,
        reference,
        "Customer",
        "SALE",
        "PLN",
        amount,
        BigDecimal.ZERO,
        amount,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        amount,
        amount,
        new BigDecimal(rate),
        "test");
  }
}
