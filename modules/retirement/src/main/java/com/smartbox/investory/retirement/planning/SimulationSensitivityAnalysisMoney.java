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
      Cell lower,
      Cell base,
      Cell higher,
      String mainEffect,
      String moreHarmfulDirection) {
    public Driver(
        String label,
        String perturbation,
        String impact,
        String reserveCoverage,
        String wealthChange,
        String status) {
      this(
          label,
          perturbation,
          impact,
          reserveCoverage,
          wealthChange,
          status,
          Cell.unavailable("Not available"),
          Cell.unavailable("Not available"),
          Cell.unavailable("Not available"),
          "—",
          "Equivalent");
    }
  }

  public record Cell(
      String testedValue,
      boolean available,
      String status,
      String firstFailureYear,
      String minimumLiquidAssets) {
    public static Cell unavailable(String reason) {
      return new Cell("—", false, reason, "—", "—");
    }
  }
}
