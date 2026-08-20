package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.simulation.SimulationYear;

/** One presentation-only timeline row. Its payload type preserves the source boundary. */
public record PlanningTimelineYear(
    int year,
    int age,
    PlanningTimelineState state,
    PastPlanningYear past,
    CurrentPlanningYear current,
    SimulationYear projection) {}
