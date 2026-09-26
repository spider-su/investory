package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.SimulationScenario;
import com.smartbox.investory.shared.currency.CurrencyType;
import lombok.Getter;
import lombok.Setter;

/** Bound navigation state for the simulation page. */
@Getter
@Setter
public final class SimulationQuery {
  private Long planId;
  private CurrencyType planningDisplayCurrency;
  private SimulationScenario selectedScenario = SimulationScenario.BASE;
}
