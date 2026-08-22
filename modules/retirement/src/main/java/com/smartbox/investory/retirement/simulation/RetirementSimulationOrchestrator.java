package com.smartbox.investory.retirement.simulation;

import com.smartbox.investory.investment.api.InvestmentAnnualProjectionApi;
import com.smartbox.investory.longterm.api.LongTermAnnualProjectionApi;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One deterministic yearly funding pipeline. Lifecycle only contributes cash flows. */
public final class RetirementSimulationOrchestrator {
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private final LongTermAnnualProjectionApi longTerm;
  private final InvestmentAnnualProjectionApi investments;

  public RetirementSimulationOrchestrator(LongTermAnnualProjectionApi longTerm,
      InvestmentAnnualProjectionApi investments) {
    this.longTerm = longTerm;
    this.investments = investments;
  }

  public Result run(RetirementSimulationInput input) {
    List<Year> years = new ArrayList<>();
    BigDecimal reserve = input.initialReserve();
    BigDecimal investmentValue = input.initialInvestmentValue();
    BigDecimal spending = input.annualExpenses();
    LongTermAnnualProjectionApi.PlanningState longTermState = input.longTermPlanningState();
    for (int age = input.currentAge(); age <= input.endAge(); age++) {
      int year = input.startYear() + age - input.currentAge();
      boolean retired = age >= input.retirementAge();
      BigDecimal expenses = retired
          ? spending.multiply(input.expenseProfileFactorForCalendarYear(year)) : ZERO;
      BigDecimal employment = retired ? ZERO : input.annualEmploymentIncome();
      BigDecimal pension = age >= input.pensionStartAge() ? input.annualPension() : ZERO;

      // 1. Every source enters aggregation as a planned cash flow.
      var longTermIncome = longTerm.plan(new LongTermAnnualProjectionApi.PlanningRequest(
          year, ZERO, longTermState));
      List<PlannedCashFlow> flows = lifecycleFlows(input, year, expenses, employment, pension);
      addLongTermFlows(flows, year, longTermIncome.plannedCashFlows());
      var cashFlow = new CashFlowAggregationService().aggregateProjected(
          new Period(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)), flows);

      // 2-6. Gap/surplus and reserve are independent of cash-flow category.
      BigDecimal fundingGap = cashFlow.fundingGap();
      BigDecimal reserveAfterTransfer = reserve.add(longTermIncome.reserveTransfer());
      BigDecimal reserveWithdrawal = reserveAfterTransfer.min(fundingGap);
      BigDecimal remaining = fundingGap.subtract(reserveWithdrawal);
      // 7. Long-Term capital is requested before investment.
      var longTermFunding = longTerm.plan(new LongTermAnnualProjectionApi.PlanningRequest(
          year, remaining, longTermState));
      remaining = remaining.subtract(longTermFunding.actualCapitalProvided()).max(ZERO);
      // 8. Investment owns returns, valuation, and withdrawal limits.
      var investment = investments.project(new InvestmentAnnualProjectionApi.ProjectionRequest(
          year, investmentValue, retired ? ZERO : input.annualPreRetirementContribution(),
          input.investmentReturnRate(), remaining, input.investmentSource()));
      // 9-10. Returned end states are the only next-year state.
      BigDecimal unfunded = remaining.subtract(investment.withdrawal()).max(ZERO);
      BigDecimal rentalIncome = amount(longTermIncome.plannedCashFlows(),
          LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME);
      BigDecimal bondIncome = amount(longTermIncome.plannedCashFlows(),
          LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME);
      BigDecimal reserveEnd = reserveAfterTransfer.subtract(reserveWithdrawal);
      years.add(new Year(age, year, retired, expenses, employment, pension,
          events(input, year, SimulationEventType.ONE_OFF_INCOME),
          events(input, year, SimulationEventType.ONE_OFF_EXPENSE),
          rentalIncome.divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP), rentalIncome, bondIncome,
          cashFlow.netCashFlow().negate(), fundingGap, cashFlow.surplus(), reserveWithdrawal,
          longTermIncome.reserveTransfer(), longTermFunding.actualCapitalProvided(),
          longTermFunding.endCapital(), investment.withdrawal(), unfunded, reserveEnd,
          investment, longTermFunding.source()));
      reserve = reserveEnd;
      investmentValue = investment.endValue();
      longTermState = longTermFunding.endState();
      if (retired) spending = spending.multiply(BigDecimal.ONE.add(input.spendingGrowthRate()));
    }
    return new Result(years);
  }

  private static List<PlannedCashFlow> lifecycleFlows(RetirementSimulationInput input, int year,
      BigDecimal expenses, BigDecimal employment, BigDecimal pension) {
    List<PlannedCashFlow> flows = new ArrayList<>();
    addAnnual(flows, "retirement-expenses", CashFlowDirection.EXPENSE, expenses, year);
    addAnnual(flows, "event-expenses", CashFlowDirection.EXPENSE,
        events(input, year, SimulationEventType.ONE_OFF_EXPENSE), year);
    addAnnual(flows, "employment", CashFlowDirection.INCOME, employment, year);
    addAnnual(flows, "pension", CashFlowDirection.INCOME, pension, year);
    addAnnual(flows, "event-income", CashFlowDirection.INCOME,
        events(input, year, SimulationEventType.ONE_OFF_INCOME), year);
    return flows;
  }

  private static void addLongTermFlows(List<PlannedCashFlow> flows, int year,
      List<LongTermAnnualProjectionApi.PlannedCashFlow> longTermFlows) {
    for (var flow : longTermFlows)
      addAnnual(flows, flow.id(), CashFlowDirection.INCOME, flow.annualAmount(), year);
  }

  private static BigDecimal amount(List<LongTermAnnualProjectionApi.PlannedCashFlow> flows,
      LongTermAnnualProjectionApi.CashFlowKind kind) {
    return flows.stream().filter(flow -> flow.kind() == kind)
        .map(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount).reduce(ZERO, BigDecimal::add);
  }

  private static BigDecimal events(RetirementSimulationInput input, int year, SimulationEventType type) {
    return input.events().stream().filter(e -> e.year() == year && e.type() == type)
        .map(SimulationEvent::amount).reduce(ZERO, BigDecimal::add);
  }

  private static void addAnnual(List<PlannedCashFlow> flows, String id, CashFlowDirection direction,
      BigDecimal amount, int year) {
    if (amount.signum() > 0) flows.add(new PlannedCashFlow(id, id, direction,
        CashFlowCadence.ANNUAL, amount, LocalDate.of(year, 1, 1), ProjectionSource.PROJECTED));
  }

  public record Result(List<Year> years) { public Result { years = List.copyOf(years); } }

  public record Year(int age, int year, boolean retired, BigDecimal expenses,
      BigDecimal employmentIncome, BigDecimal pensionIncome, BigDecimal eventIncome,
      BigDecimal eventExpenses, BigDecimal monthlyNetRentalIncome, BigDecimal annualRentalIncome, BigDecimal netBondIncome,
      BigDecimal incomeGap, BigDecimal requiredFunding, BigDecimal annualSurplus,
      BigDecimal reserveWithdrawal, BigDecimal reserveTransfer, BigDecimal maturedBondFunding,
      BigDecimal longTermCapitalEnd,
      BigDecimal investmentWithdrawal, BigDecimal unfundedShortfall, BigDecimal reserveEnd,
      InvestmentAnnualProjectionApi.AnnualProjection investment,
      LongTermAnnualProjectionApi.Source longTermSource) {}
}
