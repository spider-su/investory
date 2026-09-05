package com.smartbox.investory.testsupport.happyinvestor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Independent arithmetic checkpoints for canonical source and boundary facts. */
class HappyInvestorFactsTest {
  @Test
  void documentsCalendarAndBoundaryRentalCalculations() {
    BigDecimal calendar2025 =
        new BigDecimal("3200")
            .multiply(new BigDecimal("12"))
            .add(new BigDecimal("2800").multiply(new BigDecimal("6")))
            .add(new BigDecimal("3000").multiply(new BigDecimal("6")));
    BigDecimal boundaryAnnualized =
        new BigDecimal("3200").add(new BigDecimal("3000")).multiply(new BigDecimal("12"));
    BigDecimal boundaryTax =
        new BigDecimal("3200")
            .add(new BigDecimal("3000"))
            .multiply(new BigDecimal("12"))
            .multiply(new BigDecimal("0.085"));

    assertThat(calendar2025)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_CALENDAR_2025_GROSS);
    assertThat(boundaryAnnualized)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_GROSS_ANNUAL);
    assertThat(boundaryTax)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_TAX_ANNUAL);
    assertThat(boundaryAnnualized.subtract(boundaryTax))
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL);
  }
}
