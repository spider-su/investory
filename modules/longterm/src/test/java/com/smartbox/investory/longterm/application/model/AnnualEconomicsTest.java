package com.smartbox.investory.longterm.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AnnualEconomicsTest {
  @Test
  void derivesAnnualNetValuesAndYieldsFromOneSetOfPrimitives() {
    AnnualEconomics economics =
        AnnualEconomics.of(
            new BigDecimal("100000"),
            new BigDecimal("12000"),
            new BigDecimal("2000"),
            new BigDecimal("1500"));

    assertThat(economics.netAnnualIncomeBeforeTax()).isEqualByComparingTo("10000");
    assertThat(economics.netAnnualIncomeAfterTax()).isEqualByComparingTo("8500");
    assertThat(economics.grossYield()).isEqualByComparingTo("0.12");
    assertThat(economics.netYieldBeforeTax()).isEqualByComparingTo("0.1");
    assertThat(economics.netYieldAfterTax()).isEqualByComparingTo("0.085");
  }

  @Test
  void zeroValueProducesZeroYieldsForBothNormalAndAggregateEconomics() {
    AnnualEconomics normal =
        AnnualEconomics.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    AnnualEconomics aggregate =
        AnnualEconomics.aggregateOf(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    assertThat(normal.grossYield()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(aggregate.netYieldAfterTax()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void aggregateRetainsItsHigherPrecisionWithoutDuplicatingNetFormulas() {
    AnnualEconomics aggregate =
        AnnualEconomics.aggregateOf(
            new BigDecimal("3"), new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO);

    assertThat(aggregate.grossYield()).isEqualByComparingTo("0.333333333333");
    assertThat(aggregate.netAnnualIncomeBeforeTax()).isEqualByComparingTo("1");
  }
}
