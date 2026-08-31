package com.smartbox.investory.investment.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Modified Dietz Calculator")
class ModifiedDietzCalculatorTest {

  @DisplayName("beginning Of Day Flow Weight Keeps External Deposit Neutral")
  @Test
  void beginningOfDayFlowWeightKeepsExternalDepositNeutral() {
    assertEquals(0.0, ModifiedDietzCalculator.profit(10_000, 12_000, 2_000));
    assertEquals(0.0, ModifiedDietzCalculator.returnRate(10_000, 12_000, List.of(2_000.0)));
  }

  @DisplayName("signed Internal Transfer Keeps Profit Neutral")
  @Test
  void signedInternalTransferKeepsProfitNeutral() {
    assertEquals(0.0, ModifiedDietzCalculator.profit(10_000, 7_000, -3_000));
    assertEquals(0.0, ModifiedDietzCalculator.profit(5_000, 8_000, 3_000));
    assertEquals(0.0, ModifiedDietzCalculator.returnRate(10_000, 7_000, List.of(-3_000.0)));
    assertEquals(0.0, ModifiedDietzCalculator.returnRate(5_000, 8_000, List.of(3_000.0)));
  }

  @DisplayName("monthly Linking Is Geometric")
  @Test
  void monthlyLinkingIsGeometric() {
    double first = 0.10;
    double second = -0.05;
    assertEquals(0.045, (1 + first) * (1 + second) - 1, 1e-12);
  }
}
