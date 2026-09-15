package com.smartbox.investory.accounting;

import java.time.LocalDate;

/** Inclusive UoP insurance period. Payroll and salary are deliberately out of scope. */
public record EmploymentInsurancePeriod(
    LocalDate validFrom, LocalDate validTo, boolean qualifiesAsPrimarySocialInsuranceTitle) {
  public EmploymentInsurancePeriod {
    if (validFrom == null) throw new IllegalArgumentException("employment start date is required");
    if (validTo != null && validTo.isBefore(validFrom))
      throw new IllegalArgumentException("employment end date cannot precede start date");
  }

  public boolean activeOn(LocalDate date) {
    return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
  }
}
