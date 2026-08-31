package com.smartbox.investory.retirement.simulation;

import java.util.List;

public record SimulationSensitivityAnalysis(
    SimulationEvaluation baseline,
    List<SimulationSensitivityResult> drivers,
    String interpretation) {
  public SimulationSensitivityAnalysis {
    drivers = List.copyOf(drivers);
  }

  public List<SimulationSensitivityResult> topDrivers(int count) {
    return drivers.subList(0, Math.min(count, drivers.size()));
  }
}
