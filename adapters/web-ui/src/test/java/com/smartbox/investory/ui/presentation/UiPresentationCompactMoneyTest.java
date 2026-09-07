package com.smartbox.investory.ui.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.longterm.api.model.CashFlowType;
import com.smartbox.investory.longterm.api.model.Frequency;
import com.smartbox.investory.longterm.api.model.RentalTermView;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UI Presentation Compact Money")
class UiPresentationCompactMoneyTest {
  @DisplayName("keeps Three Display Digits For Compact Values Below Ten")
  @Test
  void keepsThreeDisplayDigitsForCompactValuesBelowTen() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("1000"))).isEqualTo("1.00K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("1123"))).isEqualTo("1.12K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("11230"))).isEqualTo("11.2K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("181000"))).isEqualTo("181.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("-114000"))).isEqualTo("-114.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("295100"))).isEqualTo("295.1K");
  }

  @DisplayName("preserves Compact Million Formatting")
  @Test
  void preservesCompactMillionFormatting() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("1000000"))).isEqualTo("1.00M");
    assertThat(UiPresentation.compactMoney(new BigDecimal("1123000"))).isEqualTo("1.12M");
    assertThat(UiPresentation.compactMoney(new BigDecimal("4550000"))).isEqualTo("4.55M");
    assertThat(UiPresentation.compactMoney(new BigDecimal("11230000"))).isEqualTo("11.2M");
  }

  @DisplayName("covers Simulation Cash Flow And Capital Examples")
  @Test
  void coversSimulationCashFlowAndCapitalExamples() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("48700"))).isEqualTo("48.7K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("109600"))).isEqualTo("109.6K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("595800"))).isEqualTo("595.8K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("900000"))).isEqualTo("900.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("1610000"))).isEqualTo("1.61M");
    assertThat(UiPresentation.compactMoney(new BigDecimal("3650000"))).isEqualTo("3.65M");
  }

  @DisplayName("trims Redundant Decimal From Long Term Header Thousands")
  @Test
  void trimsRedundantDecimalFromLongTermHeaderThousands() {
    assertThat(UiPresentation.compactMoneyTrimmed(new BigDecimal("710000"))).isEqualTo("710K");
    assertThat(UiPresentation.compactMoneyTrimmed(new BigDecimal("2642"))).isEqualTo("2.64K");
    assertThat(UiPresentation.wholeNumber(new BigDecimal("2642.49"))).isEqualTo("2,642");
  }

  @Test
  void sumsAllRentalIncomeAsMonthlyAmount() {
    assertThat(
            UiPresentation.monthlyIncome(
                List.of(
                    new RentalTermView(
                        CashFlowType.RENT, new BigDecimal("2800"), Frequency.MONTHLY, false),
                    new RentalTermView(
                        CashFlowType.PARKING_RENT, new BigDecimal("400"), Frequency.MONTHLY, false),
                    new RentalTermView(
                        CashFlowType.OTHER_INCOME,
                        new BigDecimal("1200"),
                        Frequency.ANNUAL,
                        false))))
        .isEqualByComparingTo("3300");
  }
}
