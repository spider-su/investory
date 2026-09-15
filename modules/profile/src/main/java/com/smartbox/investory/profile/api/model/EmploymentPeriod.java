package com.smartbox.investory.profile.api.model;

import java.time.LocalDate;

public record EmploymentPeriod(
    Long id,
    EmploymentType type,
    LocalDate from,
    LocalDate to,
    boolean qualifiesAsPrimarySocialInsuranceTitle) {
  public EmploymentPeriod(Long id, EmploymentType type, LocalDate from, LocalDate to) {
    this(id, type, from, to, type == EmploymentType.UOP);
  }

  public EmploymentPeriod {
    if (type == null) throw new IllegalArgumentException("employment type is required");
    if (from == null) throw new IllegalArgumentException("employment start date is required");
    if (to != null && to.isBefore(from))
      throw new IllegalArgumentException("employment end date cannot precede start date");
  }

  public boolean activeOn(LocalDate date) {
    return !date.isBefore(from) && (to == null || !date.isAfter(to));
  }
}
