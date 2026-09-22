package com.smartbox.investory.ryczalt.calculation.zus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ZusRuleSetCalculationTest {
  private final ZusCalculator calculator = new ZusCalculator();

  @Test
  void applies2025AmountsFromTheYearlyRuleSet() {
    var rules =
        new ZusRuleSet(
            2025,
            "ZUS_2025_POC_V1",
            new BigDecimal("1391.49"),
            new BigDecimal("127.49"),
            new BigDecimal("127.49"),
            new BigDecimal("461.66"),
            new BigDecimal("769.43"),
            new BigDecimal("1384.97"),
            new BigDecimal("60000"),
            new BigDecimal("300000"));

    var result =
        calculator.calculate(
            new ZusCalculationInput(true, false, "JDG", true, new BigDecimal("400000"), null),
            rules);

    assertEquals(new BigDecimal("1646.47"), result.social());
    assertEquals(new BigDecimal("1384.97"), result.health());
    assertEquals(new BigDecimal("3031.44"), result.total());
    assertEquals(new BigDecimal("1518.98"), result.deductibleSocial());
    assertEquals("ZUS_2025_POC_V1", result.ruleVersion());
  }
}
