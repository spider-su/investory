package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import com.smartbox.investory.retirement.profile.InvestmentProfile;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Canonical retirement orchestrator. Asset mechanics remain behind public module APIs. */
@Service
public class RetirementSimulationService implements RetirementSimulation {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final LongTermAnnualProjectionApi longTerm;
  private final InvestmentAnnualProjectionApi investments;

  @Autowired
  public RetirementSimulationService(LongTermAnnualProjectionApi longTerm,
      InvestmentAnnualProjectionApi investments) {
    this.longTerm = longTerm;
    this.investments = investments;
  }

  @Override
  public SimulationResult simulate(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenario scenario) { return simulate(profile, assumptions, scenario, false); }

  @Override
  public SimulationResult simulate(InvestmentProfile profile, SimulationAssumptions assumptions,
      SimulationScenario scenario, boolean actualRentalYear) {
    SimulationScenarioSettings settings = SimulationScenarioSettings.forScenario(scenario, assumptions);
    BigDecimal reserve = nz(profile.liquidAssets());
    BigDecimal investmentStart = nz(profile.marketPortfolioValue()).subtract(reserve).max(ZERO);
    // This service passes Long-Term public planning facts through unchanged.  It does not inspect
    // asset type, periods, interest treatment, tax, rental, or maturity semantics.
    var longTermState = new LongTermAnnualProjectionApi.PlanningState(
        profile.longTermPlanningState().assets(), settings.effectiveRentalIncomeGrowthRate(),
        assumptions.startYear(), actualRentalYear ? LongTermAnnualProjectionApi.Source.ACTUAL
            : profile.longTermPlanningState().source());
    var input = new RetirementSimulationInput(
        assumptions.currentAge(), assumptions.endAge(), assumptions.startYear(),
        assumptions.retirementAge(),
        assumptions.annualLivingExpenses().add(assumptions.annualDiscretionaryExpenses()),
        settings.effectiveSpendingGrowthRate(), assumptions.annualPension(), assumptions.pensionStartAge(),
        assumptions.annualEmploymentIncome(), assumptions.annualPreRetirementContribution(),
        reserve, investmentStart, settings.equityReturnRate(),
        assumptions.futureEvents(), InvestmentAnnualProjectionApi.Source.PROJECTED,
        assumptions.expenseProfile(), longTermState);
    var result = new RetirementSimulationOrchestrator(longTerm, investments).run(input);
    return map(result, scenario, assumptions, reserve, investmentStart);
  }

  @Override
  public Map<SimulationScenario, SimulationResult> compareScenarios(InvestmentProfile profile,
      SimulationAssumptions assumptions) {
    EnumMap<SimulationScenario, SimulationResult> results = new EnumMap<>(SimulationScenario.class);
    for (SimulationScenario scenario : SimulationScenario.values()) results.put(scenario, simulate(profile, assumptions, scenario));
    return results;
  }


  private static SimulationResult map(RetirementSimulationOrchestrator.Result result, SimulationScenario scenario,
      SimulationAssumptions assumptions, BigDecimal initialReserve, BigDecimal initialInvestment) {
    BigDecimal reserveStart = initialReserve, investmentStart = initialInvestment;
    Integer failureAge = null; BigDecimal firstShortfall = ZERO, totalUnfunded = ZERO;
    var years = new java.util.ArrayList<SimulationYear>();
    for (var year : result.years()) {
      var investment = year.investment();
      BigDecimal expenses = year.expenses().add(year.eventExpenses());
      BigDecimal income = year.annualRentalIncome()
          .add(year.netBondIncome()).add(year.pensionIncome()).add(year.employmentIncome()).add(year.eventIncome());
      BigDecimal spendableEnd = year.reserveEnd().add(investment.endValue());
      if (year.unfundedShortfall().signum() > 0 && failureAge == null) { failureAge = year.age(); firstShortfall = year.unfundedShortfall(); }
      totalUnfunded = totalUnfunded.add(year.unfundedShortfall());
      years.add(SimulationYear.generic(
          year.age(), year.year(), year.retired(), year.expenses(), year.eventExpenses(),
          year.employmentIncome(), year.pensionIncome(), year.eventIncome(),
          year.annualRentalIncome(), year.netBondIncome(),
          reserveStart, year.reserveWithdrawal().add(year.maturedBondFunding()), year.reserveEnd(),
          investmentStart, investment.annualReturnAmount(), year.investmentWithdrawal(), investment.endValue(),
          year.unfundedShortfall(),
          year.retired() ? ZERO : assumptions.annualPreRetirementContribution()));
      reserveStart = year.reserveEnd(); investmentStart = investment.endValue();
    }
    return new SimulationResult(scenario, failureAge != null, failureAge, firstShortfall, totalUnfunded, years);
  }

  private static BigDecimal nz(BigDecimal value) { return value == null ? ZERO : value; }

}
