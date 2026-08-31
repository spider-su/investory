package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

public record SavePlanEventCommand(
    Long portfolioId,
    Long planId,
    Long eventId,
    int year,
    String name,
    BigDecimal amount,
    SimulationEventType type,
    String notes) {}
