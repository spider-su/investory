package com.smartbox.investory.ui.retirement.simulation;

import com.smartbox.investory.profile.api.model.InvestmentProfile;
import com.smartbox.investory.retirement.api.RetirementAnnualCostsApi;
import com.smartbox.investory.retirement.api.model.AnnualCostView;
import com.smartbox.investory.ui.profile.ProfileClient;
import com.smartbox.investory.ui.profile.RetirementProfileClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Loads and assembles the model for the financial landscape page. */
@Component
public final class FinancialLandscapePageAssembler {
  private final ProfileClient profiles;
  private final RetirementProfileClient retirementProfile;
  private final AnnualCostsClient annualCosts;

  FinancialLandscapePageAssembler(
      ProfileClient profiles,
      RetirementProfileClient retirementProfile,
      AnnualCostsClient annualCosts) {
    this.profiles = profiles;
    this.retirementProfile = retirementProfile;
    this.annualCosts = annualCosts;
  }

  FinancialLandscapePageView assemble(Long portfolioId) {
    var profile = profiles.loadProfile(portfolioId);
    var cost = retirementProfile.currentYearAnnualCost(portfolioId, profile.currency());
    return assemble(profile, cost);
  }

  private FinancialLandscapePageView assemble(InvestmentProfile profile, AnnualCostView cost) {
    var portfolioId = profile.portfolioId();
    var currency = profile.currency();
    var income = profile.incomeSummary();
    BigDecimal expenses = cost.available() ? cost.amount() : null;
    BigDecimal balance =
        expenses == null ? null : income.combinedNetAnnualIncome().subtract(expenses);
    List<FinancialLandscapePageView.ExpenseGroupView> expenseGroups = new ArrayList<>();
    if (cost.available() && cost.planId() != null) {
      RetirementAnnualCostsApi.AnnualCosts costs =
          annualCosts.load(portfolioId, cost.planId(), cost.year());
      for (var group : costs.groups()) {
        if (group.monthlyAmount() != null && group.monthlyAmount().signum() > 0) {
          expenseGroups.add(
              new FinancialLandscapePageView.ExpenseGroupView(group.name(), group.monthlyAmount()));
        }
      }
      if (costs.otherMonthlyCosts() != null && costs.otherMonthlyCosts().signum() > 0) {
        expenseGroups.add(
            new FinancialLandscapePageView.ExpenseGroupView("Other", costs.otherMonthlyCosts()));
      }
      if (costs.annualExtras() != null && costs.annualExtras().signum() > 0) {
        expenseGroups.add(
            new FinancialLandscapePageView.ExpenseGroupView(
                "Annual extras",
                costs
                    .annualExtras()
                    .divide(BigDecimal.valueOf(12), 8, java.math.RoundingMode.HALF_UP)));
      }
    }
    return new FinancialLandscapePageView(
        portfolioId,
        currency.name(),
        cost.year(),
        income.combinedNetAnnualIncome(),
        income.longTermAnnualIncome(),
        income.marketNetAnnualIncome(),
        expenses,
        balance == null ? null : balance.negate().max(BigDecimal.ZERO),
        profile.liquidAssets(),
        profile.illiquidAssets(),
        profile.longTermInvestmentValue(),
        income.investmentIncomeBase() != null
            ? income.investmentIncomeBase()
            : profile.marketPortfolioValue(),
        expenseGroups);
  }

  /** Reuses the Profile page's already-loaded income and annual-cost facts. */
  public Map<String, Object> assembleCompactAttributes(
      InvestmentProfile profile, AnnualCostView cost) {
    return assemble(profile, cost).asAttributes(true);
  }
}
