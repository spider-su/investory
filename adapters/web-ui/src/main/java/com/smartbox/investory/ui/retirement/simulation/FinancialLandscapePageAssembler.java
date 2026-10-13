package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.model.PlanningTimelineState;
import com.smartbox.investory.retirement.api.model.RetirementProjection;
import com.smartbox.investory.retirement.api.model.SimulationScenario;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Loads and assembles the model for the financial landscape page. */
@Component
final class FinancialLandscapePageAssembler {
  private final RetirementPlanClient plans;
  private final RetirementProjectionClient projections;
  private final RetirementTimelineClient timeline;
  private final AnnualCostsClient annualCosts;
  private final Clock clock;

  FinancialLandscapePageAssembler(
      RetirementPlanClient plans,
      RetirementProjectionClient projections,
      RetirementTimelineClient timeline,
      AnnualCostsClient annualCosts,
      Clock clock) {
    this.plans = plans;
    this.projections = projections;
    this.timeline = timeline;
    this.annualCosts = annualCosts;
    this.clock = clock;
  }

  FinancialLandscapePageView assemble(Long portfolioId) {
    var planId = plans.resolvePlanId(portfolioId, null).orElse(null);
    RetirementProjection projection = projections.load(portfolioId, planId);
    var profile = projection.profile();
    var currency = profile.currency();
    var response = timeline.loadForwardTimeline(portfolioId, projection, SimulationScenario.BASE, currency);
    int currentYear = Year.now(clock).getValue();
    var liveYear =
        response.timeline().years().stream()
            .filter(row -> row.state() == PlanningTimelineState.LIVE)
            .findFirst()
            .orElse(null);
    var money = liveYear == null ? null : response.money().get(liveYear.year());
    List<FinancialLandscapePageView.ExpenseGroupView> expenseGroups = new ArrayList<>();
    if (planId != null && liveYear != null) {
      RetirementAnnualCostsApi.AnnualCosts costs = annualCosts.load(portfolioId, planId, liveYear.year());
      for (var group : costs.groups()) {
        if (group.monthlyAmount() != null && group.monthlyAmount().signum() > 0) {
          expenseGroups.add(new FinancialLandscapePageView.ExpenseGroupView(group.name(), group.monthlyAmount()));
        }
      }
      if (costs.otherMonthlyCosts() != null && costs.otherMonthlyCosts().signum() > 0) {
        expenseGroups.add(new FinancialLandscapePageView.ExpenseGroupView("Other", costs.otherMonthlyCosts()));
      }
      if (costs.annualExtras() != null && costs.annualExtras().signum() > 0) {
        expenseGroups.add(
            new FinancialLandscapePageView.ExpenseGroupView(
                "Annual extras",
                costs.annualExtras().divide(BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP)));
      }
    }
    return new FinancialLandscapePageView(
        portfolioId,
        currency.name(),
        liveYear == null ? currentYear : liveYear.year(),
        profile.incomeSummary().combinedNetAnnualIncome(),
        money == null ? null : money.totalIncome(),
        profile.incomeSummary().marketNetAnnualIncome(),
        profile.incomeSummary().longTermAnnualIncome(),
        money == null ? null : money.annualCosts(),
        money == null ? null : money.fundingSurplus(),
        money == null ? null : money.fundingGap(),
        profile.liquidAssets(),
        profile.illiquidAssets(),
        profile.totalNetWorth(),
        profile.retirementReserve(),
        expenseGroups);
  }
}
