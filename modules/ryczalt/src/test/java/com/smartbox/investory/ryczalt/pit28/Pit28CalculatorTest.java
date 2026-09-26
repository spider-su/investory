package com.smartbox.investory.ryczalt.pit28;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Pit28CalculatorTest {
  private final Pit28Calculator calculator = new Pit28Calculator();

  @Test
  void calculatesAnnualTaxAndAmountDueWithBothDeductions() {
    var result = calculator.calculate(facts("100000", "1000", "250", "10000"));

    assertEquals(new BigDecimal("98750"), result.taxableRevenue());
    assertEquals(new BigDecimal("11850"), result.annualTax());
    assertEquals(new BigDecimal("1850"), result.amountDue());
    assertEquals(BigDecimal.ZERO, result.overpayment());
  }

  @Test
  void reportsOverpaymentAndNeverMakesTaxableRevenueNegative() {
    var result = calculator.calculate(facts("100", "1000", "250", "1000"));

    assertEquals(BigDecimal.ZERO.setScale(0), result.taxableRevenue());
    assertEquals(BigDecimal.ZERO.setScale(0), result.annualTax());
    assertEquals(BigDecimal.ZERO.setScale(0), result.amountDue());
    assertEquals(new BigDecimal("1000"), result.overpayment());
  }

  @Test
  void zeroRevenueProducesZeroTax() {
    var result = calculator.calculate(facts("0", "0", "0", "0"));

    assertEquals(BigDecimal.ZERO.setScale(0), result.annualTax());
    assertEquals(BigDecimal.ZERO.setScale(0), result.amountDue());
  }

  private Pit28AnnualFacts facts(String revenue, String social, String health, String paid) {
    return new Pit28AnnualFacts(
        2026,
        new BigDecimal(revenue),
        Map.of("PLN", new BigDecimal(revenue)),
        new BigDecimal(social),
        new BigDecimal(social),
        new BigDecimal(health),
        new BigDecimal(health),
        new BigDecimal("0.12"),
        new BigDecimal(paid));
  }
}
