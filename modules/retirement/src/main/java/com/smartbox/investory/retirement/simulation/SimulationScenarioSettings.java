package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record SimulationScenarioSettings(
    BigDecimal inflationRate,
    BigDecimal cashReturnRate,
    BigDecimal fixedIncomeReturnRate,
    BigDecimal equityReturnRate,
    BigDecimal realEstateReturnRate,
    BigDecimal otherReturnRate,
    BigDecimal rentalIncomeGrowthRate,
    BigDecimal spendingGrowthRate) {
  public static SimulationScenarioSettings forScenario(
      SimulationScenario scenario, SimulationAssumptions a) {
    java.util.Objects.requireNonNull(scenario, "scenario");
    SimulationScenarioSettings settings;
    if (scenario == SimulationScenario.CONSERVATIVE) {
      settings =
          new SimulationScenarioSettings(
              a.inflationRate().add(new BigDecimal("0.010")),
              a.cashReturnRate().subtract(new BigDecimal("0.005")),
              a.fixedIncomeReturnRate().subtract(new BigDecimal("0.010")),
              a.equityReturnRate().subtract(new BigDecimal("0.020")),
              a.realEstateReturnRate().subtract(new BigDecimal("0.005")),
              a.otherReturnRate().subtract(new BigDecimal("0.010")),
              adjustedRentalGrowth(a.rentalIncomeGrowthRate(), new BigDecimal("-0.005")),
              a.spendingGrowthRate().add(new BigDecimal("0.005")));
    } else if (scenario == SimulationScenario.BASE) {
      settings =
          new SimulationScenarioSettings(
              a.inflationRate(),
              a.cashReturnRate(),
              a.fixedIncomeReturnRate(),
              a.equityReturnRate(),
              a.realEstateReturnRate(),
              a.otherReturnRate(),
              a.rentalIncomeGrowthRate(),
              a.spendingGrowthRate());
    } else {
      settings =
          new SimulationScenarioSettings(
              a.inflationRate().subtract(new BigDecimal("0.005")),
              a.cashReturnRate().add(new BigDecimal("0.005")),
              a.fixedIncomeReturnRate().add(new BigDecimal("0.010")),
              a.equityReturnRate().add(new BigDecimal("0.020")),
              a.realEstateReturnRate().add(new BigDecimal("0.005")),
              a.otherReturnRate().add(new BigDecimal("0.010")),
              adjustedRentalGrowth(a.rentalIncomeGrowthRate(), new BigDecimal("0.005")),
              a.spendingGrowthRate().subtract(new BigDecimal("0.005")));
    }
    settings.validateMultiplicativeRates();
    return settings;
  }

  private void validateMultiplicativeRates() {
    for (BigDecimal rate :
        java.util.List.of(
            inflationRate,
            cashReturnRate,
            fixedIncomeReturnRate,
            equityReturnRate,
            realEstateReturnRate,
            otherReturnRate,
            rentalIncomeGrowthRate,
            spendingGrowthRate)) {
      if (rate.compareTo(BigDecimal.ONE.negate()) < 0)
        throw new IllegalArgumentException("Effective scenario rate cannot be below -1");
    }
  }

  private static BigDecimal adjustedRentalGrowth(BigDecimal rate, BigDecimal adjustment) {
    BigDecimal adjusted = rate.add(adjustment);
    if (adjusted.compareTo(BigDecimal.ONE.negate()) < 0)
      throw new IllegalArgumentException("Scenario rental growth cannot be below -1");
    return adjusted;
  }
}
