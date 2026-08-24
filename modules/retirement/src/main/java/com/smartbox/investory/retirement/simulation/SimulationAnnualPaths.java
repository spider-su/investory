package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/** Optional annual inputs for Analysis runners; empty paths preserve constant-rate behavior. */
public record SimulationAnnualPaths(
    Map<Integer, BigDecimal> inflation,
    Map<Integer, BigDecimal> bondReturn,
    Map<Integer, BigDecimal> equityReturn) {
  public SimulationAnnualPaths {
    inflation = inflation == null ? Map.of() : Map.copyOf(inflation);
    bondReturn = bondReturn == null ? Map.of() : Map.copyOf(bondReturn);
    equityReturn = equityReturn == null ? Map.of() : Map.copyOf(equityReturn);
  }

  public static SimulationAnnualPaths constantRates() {
    return new SimulationAnnualPaths(Map.of(), Map.of(), Map.of());
  }

  public Optional<BigDecimal> inflationFor(int year) { return Optional.ofNullable(inflation.get(year)); }

  public Optional<BigDecimal> bondReturnFor(int year) { return Optional.ofNullable(bondReturn.get(year)); }

  public Optional<BigDecimal> equityReturnFor(int year) { return Optional.ofNullable(equityReturn.get(year)); }
}
