package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationCustomDeltas;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parses transient CUSTOM percentage-point inputs without changing the saved plan. */
public record CustomScenarioInput(
    SimulationCustomDeltas deltas,
    String inflation,
    String rentalGrowth,
    String bondReturn,
    String equityReturn,
    String spendingGrowth,
    Map<String, String> errors) {
  private static final BigDecimal MAX_PERCENTAGE_POINTS = new BigDecimal("20");

  public static CustomScenarioInput parse(
      String inflation,
      String rentalGrowth,
      String bondReturn,
      String equityReturn,
      String spendingGrowth) {
    Map<String, String> errors = new LinkedHashMap<>();
    BigDecimal inflationRate = parse("inflation", inflation, errors);
    BigDecimal rentalRate = parse("rentalGrowth", rentalGrowth, errors);
    BigDecimal bondRate = parse("bondReturn", bondReturn, errors);
    BigDecimal equityRate = parse("equityReturn", equityReturn, errors);
    BigDecimal spendingRate = parse("spendingGrowth", spendingGrowth, errors);
    return new CustomScenarioInput(
        new SimulationCustomDeltas(
            inflationRate.movePointLeft(2),
            rentalRate.movePointLeft(2),
            bondRate.movePointLeft(2),
            equityRate.movePointLeft(2),
            spendingRate.movePointLeft(2)),
        display(inflation),
        display(rentalGrowth),
        display(bondReturn),
        display(equityReturn),
        display(spendingGrowth),
        Map.copyOf(errors));
  }

  public CustomScenarioInput withError(String field, String message) {
    Map<String, String> updated = new LinkedHashMap<>(errors);
    updated.put(field, message);
    return new CustomScenarioInput(
        deltas,
        inflation,
        rentalGrowth,
        bondReturn,
        equityReturn,
        spendingGrowth,
        Map.copyOf(updated));
  }

  private static BigDecimal parse(String field, String raw, Map<String, String> errors) {
    if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
    try {
      BigDecimal value = new BigDecimal(raw.trim());
      if (value.abs().compareTo(MAX_PERCENTAGE_POINTS) > 0) {
        errors.put(field, "Use a value between -20 and +20 pp.");
        return BigDecimal.ZERO;
      }
      return value;
    } catch (NumberFormatException ex) {
      errors.put(field, "Enter a decimal percentage-point value.");
      return BigDecimal.ZERO;
    }
  }

  private static String display(String raw) {
    return raw == null || raw.isBlank() ? "0.0" : raw;
  }
}
