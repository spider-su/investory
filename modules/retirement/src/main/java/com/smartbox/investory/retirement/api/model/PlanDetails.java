package com.smartbox.investory.retirement.api.model;

public record PlanDetails(
    Long id,
    String name,
    SimulationAssumptions assumptions,
    Long currentRevisionId,
    RevisionSummary currentRevision,
    PlanningBaseline baseline) {}
