package com.smartbox.investory.retirement.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Simulation Growth Spread")
class SimulationGrowthSpreadTest {
  @DisplayName("base Growth Uses Inflation Plus Configured Spreads")
  @Test
  void baseGrowthUsesInflationPlusConfiguredSpreads() {
    SimulationAssumptions assumptions = assumptions();

    assertThat(assumptions.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo("0.030");
    assertThat(assumptions.effectiveSpendingGrowthRate()).isEqualByComparingTo("0.040");
  }

  @DisplayName("changing Inflation Keeps Spreads And Changes Both Effective Rates")
  @Test
  void changingInflationKeepsSpreadsAndChangesBothEffectiveRates() {
    SimulationAssumptions assumptions = assumptions().withInflationRate(new BigDecimal("0.030"));

    assertThat(assumptions.rentalIncomeGrowthSpread()).isEqualByComparingTo("0.005");
    assertThat(assumptions.spendingGrowthSpread()).isEqualByComparingTo("0.015");
    assertThat(assumptions.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo("0.035");
    assertThat(assumptions.effectiveSpendingGrowthRate()).isEqualByComparingTo("0.045");
  }

  @DisplayName("negative Spreads Are Valid When The Effective Rate Remains Multiplicative")
  @Test
  void negativeSpreadsAreValidWhenTheEffectiveRateRemainsMultiplicative() {
    SimulationAssumptions assumptions =
        assumptions().withRentalIncomeGrowthSpread(new BigDecimal("-0.005"));

    assertThat(assumptions.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo("0.020");
  }

  @DisplayName("effective Rates Are Inflation Plus Spread")
  @ParameterizedTest
  @MethodSource("spreadCases")
  void effectiveRatesAreInflationPlusSpread(String inflation, String spread, String expected) {
    SimulationAssumptions assumptions =
        assumptions()
            .withInflationRate(new BigDecimal(inflation))
            .withRentalIncomeGrowthSpread(new BigDecimal(spread))
            .withSpendingGrowthSpread(new BigDecimal(spread));
    assertThat(assumptions.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo(expected);
    assertThat(assumptions.effectiveSpendingGrowthRate()).isEqualByComparingTo(expected);
  }

  private static Stream<Arguments> spreadCases() {
    return Stream.of(
        Arguments.of("0.030", "-0.015", "0.015"),
        Arguments.of("0.030", "-0.020", "0.010"),
        Arguments.of("0.025", "-0.010", "0.015"),
        Arguments.of("0.025", "0.005", "0.030"),
        Arguments.of("0", "-0.01", "-0.01"));
  }

  @DisplayName("scenarios Apply Stress To Inflation And Spreads Before Deriving Nominal Growth")
  @Test
  void scenariosApplyStressToInflationAndSpreadsBeforeDerivingNominalGrowth() {
    SimulationAssumptions assumptions = assumptions();

    SimulationScenarioSettings base =
        SimulationScenarioSettings.forScenario(SimulationScenario.BASE, assumptions);
    SimulationScenarioSettings conservative =
        SimulationScenarioSettings.forScenario(SimulationScenario.CONSERVATIVE, assumptions);
    SimulationScenarioSettings optimistic =
        SimulationScenarioSettings.forScenario(SimulationScenario.OPTIMISTIC, assumptions);

    assertThat(base.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo("0.030");
    assertThat(base.effectiveSpendingGrowthRate()).isEqualByComparingTo("0.040");
    assertThat(conservative.inflationRate()).isEqualByComparingTo("0.035");
    assertThat(conservative.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo("0.025");
    assertThat(conservative.effectiveSpendingGrowthRate()).isEqualByComparingTo("0.040");
    assertThat(optimistic.inflationRate()).isEqualByComparingTo("0.020");
    assertThat(optimistic.effectiveRentalIncomeGrowthRate()).isEqualByComparingTo("0.035");
    assertThat(optimistic.effectiveSpendingGrowthRate()).isEqualByComparingTo("0.035");
  }

  private static SimulationAssumptions assumptions() {
    return SimulationAssumptions.defaults(null, 65, 70, 2026)
        .withInflationRate(new BigDecimal("0.025"))
        .withRentalIncomeGrowthSpread(new BigDecimal("0.005"))
        .withSpendingGrowthSpread(new BigDecimal("0.015"));
  }
}
