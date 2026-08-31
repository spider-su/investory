package com.smartbox.investory.retirement.api.model;

public record CreatePlanCommand(
    Long portfolioId, String name, SimulationAssumptions assumptions, PlanningBaseline baseline) {}
