package com.smartbox.investory.testsupport.happyinvestor;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Independent arithmetic checkpoints for canonical source and boundary facts. */
class HappyInvestorFactsTest {
  @Test
  void longTermTotalsIncludeBothCanonicalCashReserves() {
    assertThat(HappyInvestorLongTermFacts.CASH_RESERVE_TOTAL)
        .isEqualByComparingTo(
            HappyInvestorLongTermFacts.PLAIN_CASH_RESERVE_PRINCIPAL.add(
                HappyInvestorLongTermFacts.INTEREST_BEARING_RESERVE_PRINCIPAL));
    assertThat(HappyInvestorLongTermFacts.LONG_TERM_TOTAL)
        .isEqualByComparingTo(
            HappyInvestorLongTermFacts.REAL_ESTATE_TOTAL
                .add(HappyInvestorLongTermFacts.BOND_TOTAL)
                .add(HappyInvestorLongTermFacts.CASH_RESERVE_TOTAL)
                .add(HappyInvestorLongTermFacts.PERSONAL_ASSET_TOTAL));
  }

  @Test
  void documentsCalendarAndBoundaryRentalCalculations() {
    BigDecimal calendar2025 =
        new BigDecimal("3200")
            .multiply(new BigDecimal("12"))
            .add(new BigDecimal("2800").multiply(new BigDecimal("6")))
            .add(new BigDecimal("3000").multiply(new BigDecimal("6")));
    BigDecimal boundaryAnnualized =
        new BigDecimal("3200").add(new BigDecimal("3000")).multiply(new BigDecimal("12"));
    BigDecimal apartmentATax =
        HappyInvestorLongTermFacts.APARTMENT_A_ANNUAL_TAX_BASE.multiply(
            HappyInvestorLongTermFacts.RENTAL_TAX_RATE);
    BigDecimal apartmentBTax =
        HappyInvestorLongTermFacts.APARTMENT_B_ANNUAL_TAX_BASE.multiply(
            HappyInvestorLongTermFacts.RENTAL_TAX_RATE);
    BigDecimal boundaryTax = apartmentATax.add(apartmentBTax);

    assertThat(calendar2025)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_CALENDAR_2025_GROSS);
    assertThat(boundaryAnnualized)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_GROSS_ANNUAL);
    assertThat(apartmentATax).isEqualByComparingTo("272");
    assertThat(apartmentBTax).isEqualByComparingTo("255");
    assertThat(boundaryTax)
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_TAX_ANNUAL);
    assertThat(boundaryAnnualized.subtract(boundaryTax))
        .isEqualByComparingTo(HappyInvestorLongTermFacts.RENTAL_BOUNDARY_DATE_NET_ANNUAL);
  }
}
