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
    return "redirect:/portfolios/"
        + portfolioId
        + "/simulation/timeline/"
        + year
        + "?planningDisplayCurrency="
        + displayCurrency
        + (planId == null ? "" : "&planId=" + planId)
        + ((planId == null && scenario == SimulationScenario.BASE)
            ? ""
            : "&selectedScenario=" + scenario);
  }

  static String simulation(
      Long portfolioId, Long planId, CurrencyType displayCurrency, SimulationScenario scenario) {
    return "redirect:/portfolios/"
        + portfolioId
        + "/simulation"
        + (planId == null
            ? "?planningDisplayCurrency="
            : "?planId=" + planId + "&planningDisplayCurrency=")
        + displayCurrency
        + "&selectedScenario="
        + scenario;
  }

  static String editPlan(
      Long portfolioId, Long planId, CurrencyType displayCurrency, SimulationScenario scenario) {
    return "redirect:/portfolios/"
        + portfolioId
        + "/simulation/plan/edit"
        + (planId == null
            ? "?planningDisplayCurrency="
            : "?planId=" + planId + "&planningDisplayCurrency=")
        + displayCurrency
        + "&selectedScenario="
        + scenario;
  }
}
