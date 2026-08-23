package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.profile.InvestmentProfile;
import com.smartbox.investory.retirement.simulation.SimulationAssumptions;

/** Canonical profile and base assumptions before transient Simulation-page overrides are applied. */
public record RetirementProjectionInput(InvestmentProfile profile, SimulationAssumptions assumptions) {}
