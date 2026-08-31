package com.smartbox.investory.retirement.api.model;

public record UpdatePlanCommand(
    Long portfolioId, Long planId, String name, SimulationAssumptions assumptions) {}
