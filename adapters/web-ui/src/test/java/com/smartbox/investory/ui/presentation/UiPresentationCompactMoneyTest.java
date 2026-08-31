package com.smartbox.investory.ui.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UI Presentation Compact Money")
class UiPresentationCompactMoneyTest {
  @DisplayName("keeps One Decimal For All Compact Thousands Values")
  @Test
  void keepsOneDecimalForAllCompactThousandsValues() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("1000"))).isEqualTo("1.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("181000"))).isEqualTo("181.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("-114000"))).isEqualTo("-114.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("295100"))).isEqualTo("295.1K");
  }

  @DisplayName("preserves Compact Million Formatting")
  @Test
  void preservesCompactMillionFormatting() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("1000000"))).isEqualTo("1M");
    assertThat(UiPresentation.compactMoney(new BigDecimal("4550000"))).isEqualTo("4.55M");
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
}
