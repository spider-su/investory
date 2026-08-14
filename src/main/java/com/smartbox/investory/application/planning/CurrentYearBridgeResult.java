package com.smartbox.investory.application.planning;

import com.smartbox.investory.application.profile.InvestmentProfile;
import com.smartbox.investory.application.simulation.SimulationEvent;
import com.smartbox.investory.application.simulation.SimulationLifecyclePhase;
import java.math.BigDecimal;
import java.util.List;

/** Immutable year-end handoff from the live profile to the next full simulation year. */
public record CurrentYearBridgeResult(
    InvestmentProfile bridgedProfile,
    int asOfYear,
    int nextProjectedYear,
    SimulationLifecyclePhase lifecyclePhase,
    BigDecimal fractionApplied,
    BigDecimal contributionApplied,
    BigDecimal retirementSpendingApplied,
    BigDecimal requiredPortfolioFunding,
    BigDecimal passiveIncomeUsed,
    BigDecimal pensionIncomeUsed,
    BigDecimal contractualIncomeApplied,
    BigDecimal redemptionCashApplied,
    List<SimulationEvent> currentYearEventsApplied) {

  public CurrentYearBridgeResult {
    if (bridgedProfile == null
        || lifecyclePhase == null
        || fractionApplied == null
        || contributionApplied == null
        || retirementSpendingApplied == null
        || requiredPortfolioFunding == null
        || passiveIncomeUsed == null
        || pensionIncomeUsed == null
        || contractualIncomeApplied == null
        || redemptionCashApplied == null
        || currentYearEventsApplied == null) {
      throw new IllegalArgumentException("Bridge result requires complete values");
    }
    currentYearEventsApplied = List.copyOf(currentYearEventsApplied);
  }
}
