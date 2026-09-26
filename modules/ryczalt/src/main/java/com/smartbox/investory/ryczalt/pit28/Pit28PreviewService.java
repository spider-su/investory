package com.smartbox.investory.ryczalt.pit28;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Pit28PreviewService {
  private final Pit28AnnualFactsService facts;
  private final Pit28Calculator calculator;

  public Pit28PreviewService(Pit28AnnualFactsService facts) {
    this.facts = facts;
    this.calculator = new Pit28Calculator();
  }

  @Transactional(readOnly = true)
  public Pit28Draft preview(long profileId, int year) {
    Pit28AnnualFactsService.Result result = facts.collect(profileId, year);
    Pit28Calculation calculation =
        result.readiness().status() == Pit28ReadinessStatus.READY
            ? calculator.calculate(result.facts())
            : null;
    return new Pit28Draft(year, result.readiness(), result.facts(), calculation, result.monthly());
  }
}
