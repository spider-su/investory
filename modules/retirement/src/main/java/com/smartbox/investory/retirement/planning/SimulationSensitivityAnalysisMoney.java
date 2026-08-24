package com.smartbox.investory.retirement.planning;

import java.util.List;

public record SimulationSensitivityAnalysisMoney(String interpretation, List<Driver> drivers) {
  public SimulationSensitivityAnalysisMoney {
    drivers = List.copyOf(drivers);
  }

  public record Driver(
      String label,
      String perturbation,
      String impact,
      String reserveCoverage,
      String wealthChange,
      String status,
      String lower,
      String base,
      String higher,
      String mainEffect) {
    public Driver(String label, String perturbation, String impact, String reserveCoverage,
        String wealthChange, String status) {
      this(label, perturbation, impact, reserveCoverage, wealthChange, status, "—", "—", "—", "—");
    }
  }
}
