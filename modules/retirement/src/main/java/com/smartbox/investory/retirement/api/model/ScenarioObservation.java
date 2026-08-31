package com.smartbox.investory.retirement.api.model;

import java.math.BigDecimal;

public record ScenarioObservation(
    BigDecimal value, String label, String period, ScenarioObservationAvailability availability) {}
