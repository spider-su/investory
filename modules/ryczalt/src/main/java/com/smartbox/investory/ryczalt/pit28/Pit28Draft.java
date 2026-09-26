package com.smartbox.investory.ryczalt.pit28;

import java.util.List;

public record Pit28Draft(
    int year,
    Pit28Readiness readiness,
    Pit28AnnualFacts facts,
    Pit28Calculation calculation,
    List<Pit28AnnualFactsService.Monthly> monthlyReconciliation) {
  public Pit28Draft {
    monthlyReconciliation = List.copyOf(monthlyReconciliation);
  }
}
