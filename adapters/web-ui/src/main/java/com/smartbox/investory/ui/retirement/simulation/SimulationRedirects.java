package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;

/** Builds simulation navigation targets shared by page and command handlers. */
final class SimulationRedirects {
  private SimulationRedirects() {}

  static String planningYear(
      Long portfolioId,
      int year,
      CurrencyType displayCurrency,
      Long planId,
      SimulationScenario scenario) {
    return "redirect:/simulation/timeline/"
        + year
        + "?portfolioId="
        + portfolioId
        + "&planningDisplayCurrency="
        + displayCurrency
        + (planId == null ? "" : "&planId=" + planId)
        + ((planId == null && scenario == SimulationScenario.BASE)
            ? ""
            : "&selectedScenario=" + scenario);
  }

  static String simulation(
      Long portfolioId, Long planId, CurrencyType displayCurrency, SimulationScenario scenario) {
    return "redirect:/simulation?portfolioId="
        + portfolioId
        + (planId == null ? "" : "&planId=" + planId)
        + "&planningDisplayCurrency="
        + displayCurrency
        + "&selectedScenario="
        + scenario;
  }

  static String editPlan(
      Long portfolioId, Long planId, CurrencyType displayCurrency, SimulationScenario scenario) {
    return "redirect:/simulation/plan/edit?portfolioId="
        + portfolioId
        + (planId == null ? "" : "&planId=" + planId)
        + "&planningDisplayCurrency="
        + displayCurrency
        + "&selectedScenario="
        + scenario;
  }
}
