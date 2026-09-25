package com.smartbox.investory.testsupport.happyinvestor.ryczalt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class HappyInvestorRyczalt2026ExpectedTest {
  @Test
  void statesIndependentSevenMonthCalculationOracle() {
    var expected = HappyInvestorRyczalt2026Expected.months();

    assertThat(expected).hasSize(7);
    assertThat(expected)
        .extracting(HappyInvestorRyczalt2026Expected.Month::month)
        .containsExactly(
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 2),
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6),
            YearMonth.of(2026, 7));
    assertThat(expected.get(0).insuranceReason()).isEqualTo("UOP_PRIMARY_INSURANCE");
    assertThat(expected.get(1).zus()).isEqualByComparingTo("830.58");
    assertThat(expected.get(2).insuranceReason()).isEqualTo("JDG_PRIMARY_INSURANCE");
    assertThat(expected.get(6).zus()).isEqualByComparingTo("3283.33");
    assertThat(expected.get(6).healthBand()).isEqualTo("HIGH");
  }
}
