package com.smartbox.investory.retirement.planning;

import com.smartbox.investory.retirement.api.model.*;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances planning-year reporting state to the calendar boundary.
 *
 * <p>This service never builds a portfolio and never runs the simulator. Historical values remain
 * planning/reporting data; the live InvestmentProfile remains the forward simulation source.
 */
@Service
public class AnnualPlanningRolloverService {
  private final PlanningTimelineFacade timeline;
  private final Clock clock;

  public AnnualPlanningRolloverService(PlanningTimelineFacade timeline, Clock clock) {
    this.timeline = timeline;
    this.clock = clock;
  }

  @Transactional
  public AnnualPlanningRolloverResult rollover(Long portfolioId) {
    int currentYear = Year.now(clock).getValue();
    List<Integer> pending = new ArrayList<>();

    for (int year : timeline.historicalYears(portfolioId)) {
      PlanningYearStatus before = timeline.pastYear(portfolioId, year).status();
      timeline.createHistoricalDraft(portfolioId, year);
      if (before == PlanningYearStatus.CLOSED) continue;

      // Calendar progression exposes a year for review; only the user may close it.
      pending.add(year);
    }

    boolean currentCreated = timeline.ensureCurrentYear(portfolioId);
    return new AnnualPlanningRolloverResult(currentYear, currentCreated, List.of(), pending);
  }
}
