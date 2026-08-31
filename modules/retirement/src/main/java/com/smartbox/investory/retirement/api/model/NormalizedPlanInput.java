package com.smartbox.investory.retirement.api.model;

import java.util.List;

public record NormalizedPlanInput(
    SimulationAssumptions assumptions, List<PlanInputWarning> warnings) {}
