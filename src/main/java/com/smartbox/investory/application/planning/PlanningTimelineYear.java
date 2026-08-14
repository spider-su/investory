package com.smartbox.investory.application.planning;

import com.smartbox.investory.application.simulation.SimulationYear;

/** One presentation-only timeline row. Its payload type preserves the source boundary. */
public record PlanningTimelineYear(
    int year,
    int age,
    PlanningTimelineState state,
    PastPlanningYear past,
    CurrentPlanningYear current,
    SimulationYear projection) {}
