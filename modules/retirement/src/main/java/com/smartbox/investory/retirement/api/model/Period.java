package com.smartbox.investory.retirement.api.model;

import java.time.LocalDate;

public record Period(LocalDate start, LocalDate end) {
  public Period {
    if (start == null || end == null || end.isBefore(start)) {
      throw new IllegalArgumentException("Invalid period");
    }
  }
}
