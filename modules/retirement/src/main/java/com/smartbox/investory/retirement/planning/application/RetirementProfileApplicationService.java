package com.smartbox.investory.retirement.planning.application;

import com.smartbox.investory.retirement.analysis.*;
import com.smartbox.investory.retirement.api.RetirementPlanApi;
import com.smartbox.investory.retirement.api.RetirementProfileApi;
import com.smartbox.investory.retirement.api.model.*;
import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.retirement.planning.input.*;
import com.smartbox.investory.retirement.planning.presentation.*;
import com.smartbox.investory.retirement.planning.projection.*;
import com.smartbox.investory.retirement.planning.reconciliation.*;
import com.smartbox.investory.retirement.planning.review.*;
import com.smartbox.investory.retirement.planning.timeline.*;
import com.smartbox.investory.retirement.preview.*;
import com.smartbox.investory.shared.currency.CurrencyType;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetirementProfileApplicationService implements RetirementProfileApi {
  private final RetirementPlanApi plans;
  private final RetirementProjectionService projections;
  private final PlanningCurrencyPresentationService presentation;
  private final Clock clock;

  @Override
  @Transactional(readOnly = true)
  public AnnualCostView currentYearAnnualCost(Long portfolioId, CurrencyType reportingCurrency) {
    int year = LocalDate.now(clock).getYear();
    var planId = plans.resolvePlanId(portfolioId, null);
    if (planId.isEmpty()) return AnnualCostView.unavailable(reportingCurrency, year);
    var spending = projections.currentYearSpending(portfolioId, planId.get());
    var cost = spending.annualSpending();
    return new AnnualCostView(
        true,
        presentation.toDisplay(cost, reportingCurrency),
        reportingCurrency,
        year,
        planId.get());
  }
}
