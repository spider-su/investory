package com.smartbox.investory.integrations.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionPriceAlertServiceTest {
  @Test
  void defaultsUseSymmetricTenPercentAroundWeightedCost() {
    assertThat(
            PositionPriceAlertService.target(
                new BigDecimal("400"), "LOSS", "PERCENT", new BigDecimal("10")))
        .isEqualByComparingTo("360");
    assertThat(
            PositionPriceAlertService.target(
                new BigDecimal("400"), "PROFIT", "PERCENT", new BigDecimal("10")))
        .isEqualByComparingTo("440");
  }

  @Test
  void fixedPriceTargetsDoNotDependOnAverageCost() {
    assertThat(
            PositionPriceAlertService.target(
                new BigDecimal("400"), "PROFIT", "PRICE", new BigDecimal("475")))
        .isEqualByComparingTo("475");
  }

  @Test
  void hysteresisRequiresTwoPercentagePointsInsideTrigger() {
    BigDecimal cost = new BigDecimal("100");
    BigDecimal profitTarget = new BigDecimal("110");
    BigDecimal lossTarget = new BigDecimal("90");
    assertThat(
            PositionPriceAlertService.isRearmed(
                new BigDecimal("109"), profitTarget, cost, "PROFIT"))
        .isFalse();
    assertThat(
            PositionPriceAlertService.isRearmed(
                new BigDecimal("108"), profitTarget, cost, "PROFIT"))
        .isTrue();
    assertThat(PositionPriceAlertService.isRearmed(new BigDecimal("91"), lossTarget, cost, "LOSS"))
        .isFalse();
    assertThat(PositionPriceAlertService.isRearmed(new BigDecimal("92"), lossTarget, cost, "LOSS"))
        .isTrue();
  }
}
