package com.smartbox.investory.ui.retirement;

import java.util.Map;

/** Compact form state for the transient CUSTOM sandbox. */
public record CustomScenarioView(
    String inflation,
    String rentalGrowth,
    String bondReturn,
    String equityReturn,
    String spendingGrowth,
    Map<String, String> errors) {
  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public String error(String field) {
    return errors.get(field);
  }

  static CustomScenarioView from(CustomScenarioInput input) {
    return new CustomScenarioView(
        input.inflation(), input.rentalGrowth(), input.bondReturn(), input.equityReturn(),
        input.spendingGrowth(), input.errors());
  }
}
