package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.CurrentPlanningYear;
import com.smartbox.investory.retirement.api.model.PlanningTimelineMoney;
import com.smartbox.investory.retirement.api.model.SimulationAssumptions;
import com.smartbox.investory.shared.currency.CurrencyType;

/** Presentation model for the canonical current-year outlook. */
public record LiveYearReviewView(
    int year,
    int age,
    CurrencyType currency,
    CurrentPlanningYear current,
    PlanningTimelineMoney money,
    SimulationAssumptions plan) {

  public java.math.BigDecimal planSpending() {
    return plan.annualLivingExpenses().add(plan.annualDiscretionaryExpenses());
  }

  public java.math.BigDecimal forecastVariance() {
    return money.annualCosts() == null ? null : money.annualCosts().subtract(planSpending());
  }
}
