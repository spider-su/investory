package com.smartbox.investory.application.longterm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smartbox.investory.infrastructure.longterm.InterestTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BondPlanningCalculatorTest {
  @Test
  void preservesGrossInterestTaxNetYieldMaturityAndTreatment() {
    BondPlanningSummary result =
        new BondPlanningCalculator()
            .calculate(
                new BigDecimal("100000"),
                new BigDecimal("0.06"),
                LocalDate.of(2028, 2, 28),
                InterestTreatment.CAPITALIZE);

    assertEquals(new BigDecimal("6000.00"), result.grossInterest());
    assertEquals(new BigDecimal("1140.0000"), result.annualTax());
    assertEquals(new BigDecimal("4860.0000"), result.netInterest());
    assertEquals(new BigDecimal("0.04860000"), result.netYield());
    assertEquals(LocalDate.of(2028, 2, 28), result.maturityDate());
    assertEquals(InterestTreatment.CAPITALIZE, result.interestTreatment());
  }
}
