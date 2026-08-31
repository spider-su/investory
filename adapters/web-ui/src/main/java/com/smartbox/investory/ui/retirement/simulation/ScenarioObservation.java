package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import java.math.BigDecimal;

/** Factual, optional comparison value owned by another Investory module. */
public record ScenarioObservation(
    BigDecimal value,
    String label,
    String period,
    ScenarioAssumptionView.Availability availability) {
  public static ScenarioObservation unavailable() {
    return new ScenarioObservation(
        null, null, null, ScenarioAssumptionView.Availability.UNAVAILABLE);
  }
}
