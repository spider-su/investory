package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record SimulationScenarioSettings(
    BigDecimal inflationRate,
    BigDecimal cashReturnRate,
    BigDecimal fixedIncomeReturnRate,
    BigDecimal equityReturnRate,
    BigDecimal realEstateReturnRate,
    BigDecimal otherReturnRate,
    BigDecimal rentalIncomeGrowthSpread,
    BigDecimal spendingGrowthSpread) {
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
              a.rentalIncomeGrowthSpread().subtract(new BigDecimal("0.005")),
              a.spendingGrowthSpread().add(new BigDecimal("0.005")));
    } else if (scenario == SimulationScenario.BASE) {
      settings =
          new SimulationScenarioSettings(
              a.inflationRate(),
              a.cashReturnRate(),
              a.fixedIncomeReturnRate(),
              a.equityReturnRate(),
              a.realEstateReturnRate(),
              a.otherReturnRate(),
              a.rentalIncomeGrowthSpread(),
              a.spendingGrowthSpread());
    } else {
      settings =
          new SimulationScenarioSettings(
              a.inflationRate().subtract(new BigDecimal("0.005")),
              a.cashReturnRate().add(new BigDecimal("0.005")),
              a.fixedIncomeReturnRate().add(new BigDecimal("0.010")),
              a.equityReturnRate().add(new BigDecimal("0.020")),
              a.realEstateReturnRate().add(new BigDecimal("0.005")),
              a.otherReturnRate().add(new BigDecimal("0.010")),
              a.rentalIncomeGrowthSpread().add(new BigDecimal("0.005")),
              a.spendingGrowthSpread().subtract(new BigDecimal("0.005")));
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
            effectiveRentalIncomeGrowthRate(),
            effectiveSpendingGrowthRate())) {
      if (rate.compareTo(BigDecimal.ONE.negate()) < 0)
        throw new IllegalArgumentException("Effective scenario rate cannot be below -1");
    }
  }

  /** Nominal growth is always scenario inflation plus the asset or spending spread. */
  public BigDecimal effectiveRentalIncomeGrowthRate() {
    return effectiveGrowthRate(inflationRate, rentalIncomeGrowthSpread);
  }

  public BigDecimal effectiveSpendingGrowthRate() {
    return effectiveGrowthRate(inflationRate, spendingGrowthSpread);
  }

  static BigDecimal effectiveGrowthRate(BigDecimal inflationRate, BigDecimal growthSpread) {
    return inflationRate.add(growthSpread);
  }
}
