package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;

public record SimulationEvent(
    Long id, int year, String name, BigDecimal amount, SimulationEventType type, String notes) {
  public SimulationEvent {
    if (year < 1900
        || name == null
        || name.isBlank()
        || amount == null
        || amount.signum() < 0
        || type == null) {
      throw new IllegalArgumentException("Invalid simulation event");
    }
  }
}
