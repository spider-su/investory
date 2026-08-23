package com.smartbox.investory.ui.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UiPresentationCompactMoneyTest {
  @Test
  void keepsOneDecimalForAllCompactThousandsValues() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("1000"))).isEqualTo("1.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("181000"))).isEqualTo("181.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("-114000"))).isEqualTo("-114.0K");
    assertThat(UiPresentation.compactMoney(new BigDecimal("295100"))).isEqualTo("295.1K");
  }

  @Test
  void preservesCompactMillionFormatting() {
    assertThat(UiPresentation.compactMoney(new BigDecimal("1000000"))).isEqualTo("1M");
    assertThat(UiPresentation.compactMoney(new BigDecimal("4550000"))).isEqualTo("4.55M");
  }
}
