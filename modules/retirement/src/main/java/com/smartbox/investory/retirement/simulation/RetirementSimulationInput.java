package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import java.math.BigDecimal;
import java.util.List;

/** Explicit prepared inputs for the deterministic yearly retirement orchestrator. */
public record RetirementSimulationInput(
    int currentAge,
    int endAge,
    int startYear,
    int retirementAge,
    BigDecimal annualExpenses,
    BigDecimal spendingGrowthRate,
    BigDecimal annualPension,
    int pensionStartAge,
    BigDecimal annualEmploymentIncome,
    BigDecimal annualPreRetirementContribution,
    BigDecimal initialReserve,
    BigDecimal initialInvestmentValue,
    BigDecimal investmentReturnRate,
    List<SimulationEvent> events,
    InvestmentAnnualProjectionApi.Source investmentSource,
    ExpenseProfile expenseProfile,
    LongTermAnnualProjectionApi.PlanningState longTermPlanningState) {
  public RetirementSimulationInput {
    if (currentAge < 0 || endAge < currentAge || retirementAge < 0 || retirementAge > endAge)
      throw new IllegalArgumentException("Invalid retirement horizon");
    annualExpenses = nz(annualExpenses);
    spendingGrowthRate = nz(spendingGrowthRate);
    annualPension = nz(annualPension);
    annualEmploymentIncome = nz(annualEmploymentIncome);
    annualPreRetirementContribution = nz(annualPreRetirementContribution);
    initialReserve = nz(initialReserve);
    initialInvestmentValue = nz(initialInvestmentValue);
    investmentReturnRate = nz(investmentReturnRate);
    events = events == null ? List.of() : List.copyOf(events);
    investmentSource = investmentSource == null ? InvestmentAnnualProjectionApi.Source.PROJECTED : investmentSource;
    expenseProfile = expenseProfile == null ? ExpenseProfile.EMPTY : expenseProfile;
    longTermPlanningState = longTermPlanningState == null
        ? LongTermAnnualProjectionApi.PlanningState.EMPTY : longTermPlanningState;
  }

  /** Resolve a persisted expense stage against the original plan-year anchor. */
  public BigDecimal expenseProfileFactorForCalendarYear(int calendarYear) {
    return expenseProfile.factorForYear(calendarYear - startYear);
  }

  private static BigDecimal nz(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
