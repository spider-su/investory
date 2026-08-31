package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

/** Runtime scenario overlay. Cash and non-modeled capital-return fields are compatibility only. */
public record SimulationScenarioSettings(
    BigDecimal inflationRate,
    @Deprecated BigDecimal cashReturnRate,
    BigDecimal fixedIncomeReturnRate,
    BigDecimal equityReturnRate,
    @Deprecated BigDecimal realEstateReturnRate,
    @Deprecated BigDecimal otherReturnRate,
    BigDecimal rentalIncomeGrowthSpread,
    BigDecimal spendingGrowthSpread) {
  public static SimulationScenarioSettings forScenario(
      SimulationScenario scenario, SimulationAssumptions a) {
    return forScenario(scenario, a, SimulationCustomDeltas.zero());
  }

  public static SimulationScenarioSettings forScenario(
      SimulationScenario scenario, SimulationAssumptions a, SimulationCustomDeltas custom) {
    java.util.Objects.requireNonNull(scenario, "scenario");
    custom = custom == null ? SimulationCustomDeltas.zero() : custom;
    SimulationScenarioSettings settings;
    if (scenario == SimulationScenario.CUSTOM && !custom.isZero()) {
      settings =
          overlay(
              a,
              custom.inflation(),
              custom.rentalGrowth(),
              custom.bondReturn(),
              custom.equityReturn(),
              custom.spendingGrowth());
    } else if (scenario == SimulationScenario.CONSERVATIVE) {
      settings = overlay(a, "0.010", "-0.005", "-0.010", "-0.020", "0.0");
    } else if (scenario == SimulationScenario.BASE || scenario == SimulationScenario.CUSTOM) {
      settings = overlay(a, "0.0", "0.0", "0.0", "0.0", "0.0");
    } else {
      settings = overlay(a, "-0.005", "0.005", "0.0", "0.010", "0.005");
    }
    settings.validateMultiplicativeRates();
    return settings;
  }

  private static SimulationScenarioSettings overlay(
      SimulationAssumptions a,
      String inflationDelta,
      String rentalGrowthDelta,
      String bondReturnDelta,
      String equityReturnDelta,
      String spendingGrowthDelta) {
    BigDecimal inflation = new BigDecimal(inflationDelta);
    BigDecimal rental = new BigDecimal(rentalGrowthDelta);
    BigDecimal spending = new BigDecimal(spendingGrowthDelta);
    return new SimulationScenarioSettings(
        a.inflationRate().add(inflation),
        a.cashReturnRate(),
        a.fixedIncomeReturnRate().add(new BigDecimal(bondReturnDelta)),
        a.equityReturnRate().add(new BigDecimal(equityReturnDelta)),
        a.realEstateReturnRate(),
        a.otherReturnRate(),
        a.rentalIncomeGrowthSpread().add(rental.subtract(inflation)),
        a.spendingGrowthSpread().add(spending.subtract(inflation)));
  }

  private static SimulationScenarioSettings overlay(
      SimulationAssumptions a,
      BigDecimal inflationDelta,
      BigDecimal rentalGrowthDelta,
      BigDecimal bondReturnDelta,
      BigDecimal equityReturnDelta,
      BigDecimal spendingGrowthDelta) {
    return new SimulationScenarioSettings(
        a.inflationRate().add(inflationDelta),
        a.cashReturnRate(),
        a.fixedIncomeReturnRate().add(bondReturnDelta),
        a.equityReturnRate().add(equityReturnDelta),
        a.realEstateReturnRate(),
        a.otherReturnRate(),
        a.rentalIncomeGrowthSpread().add(rentalGrowthDelta.subtract(inflationDelta)),
        a.spendingGrowthSpread().add(spendingGrowthDelta.subtract(inflationDelta)));
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
