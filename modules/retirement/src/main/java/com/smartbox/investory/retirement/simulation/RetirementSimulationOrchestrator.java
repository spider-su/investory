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
  private final RetirementFundingAllocator fundingAllocator = new RetirementFundingAllocator();
  private final RetirementReserveRebalancer reserveRebalancer = new RetirementReserveRebalancer();

  public RetirementSimulationOrchestrator(
      LongTermAnnualProjectionApi longTerm, InvestmentAnnualProjectionApi investments) {
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
      BigDecimal expenses =
          retired ? spending.multiply(input.expenseProfileFactorForCalendarYear(year)) : ZERO;
      BigDecimal employment = retired ? ZERO : input.annualEmploymentIncome();
      BigDecimal pension = age >= input.pensionStartAge() ? input.annualPension() : ZERO;

      // 1. Every source enters aggregation as a planned cash flow.
      var longTermQuote =
          longTerm.quote(
              new LongTermAnnualProjectionApi.PlanningRequest(year, ZERO, longTermState));
      longTermQuote = applyIncomePolicy(longTermQuote, year, longTermState, input);
      List<PlannedCashFlow> flows = lifecycleFlows(input, year, expenses, employment, pension);
      addLongTermFlows(flows, year, longTermQuote.plannedCashFlows());
      var cashFlow =
          new CashFlowAggregationService()
              .aggregateProjected(
                  new Period(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)), flows);

      // 2-6. Gap/surplus and withdrawals follow the explicit policy order.
      BigDecimal fundingGap = cashFlow.fundingGap();
      BigDecimal reserveAfterTransfer = reserve.add(longTermQuote.reserveTransfer());
      BigDecimal reserveWithdrawal = ZERO;
      BigDecimal remaining = fundingGap;
      LongTermAnnualProjectionApi.PlanningProjection longTermFunding = null;
      InvestmentAnnualProjectionApi.AnnualProjection investment = null;
      for (RetirementFundingSource source : input.fundingPolicy().economicFundingOrder()) {
        if (remaining.signum() == 0) break;
        if (source == RetirementFundingSource.RESERVE) {
          var allocation =
              fundingAllocator.allocateReserve(
                  remaining,
                  reserveAfterTransfer.subtract(reserveWithdrawal),
                  input.fundingPolicy());
          reserveWithdrawal = reserveWithdrawal.add(allocation.reserveWithdrawal());
          remaining = allocation.remainingGap();
        } else if (source == RetirementFundingSource.LONG_TERM) {
          longTermFunding =
              longTerm.plan(
                  new LongTermAnnualProjectionApi.PlanningRequest(year, remaining, longTermState));
          remaining = remaining.subtract(longTermFunding.actualCapitalProvided()).max(ZERO);
        } else if (source == RetirementFundingSource.INVESTMENT
            && input.fundingPolicy().allowInvestmentWithdrawal()) {
          investment =
              investments.project(
                  new InvestmentAnnualProjectionApi.ProjectionRequest(
                      year,
                      investmentValue,
                      retired ? ZERO : input.annualPreRetirementContribution(),
                      input.investmentReturnRate(),
                      remaining,
                      input.investmentSource()));
          remaining = remaining.subtract(investment.withdrawal()).max(ZERO);
        }
      }
      if (longTermFunding == null) {
        longTermFunding =
            longTerm.plan(
                new LongTermAnnualProjectionApi.PlanningRequest(year, ZERO, longTermState));
      }
      // Investment still projects returns when it is not an allowed withdrawal source.
      if (investment == null)
        investment =
            investments.project(
                new InvestmentAnnualProjectionApi.ProjectionRequest(
                    year,
                    investmentValue,
                    retired ? ZERO : input.annualPreRetirementContribution(),
                    input.investmentReturnRate(),
                    ZERO,
                    input.investmentSource()));
      // 8. A positive return may refill reserve after spending withdrawals.
      BigDecimal recurringIncome =
          amount(
                  longTermQuote.plannedCashFlows(),
                  LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME)
              .add(
                  amount(
                      longTermQuote.plannedCashFlows(),
                      LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME))
              .add(employment)
              .add(pension);
      BigDecimal reserveTarget =
          input
              .fundingPolicy()
              .reserveTargetYears()
              .multiply(expenses.subtract(recurringIncome).max(ZERO));
      var rebalance =
          reserveRebalancer.rebalance(
              reserveAfterTransfer.subtract(reserveWithdrawal),
              reserveTarget,
              investment,
              input.investmentReturnRate(),
              input.fundingPolicy());
      investment = rebalance.investment();
      BigDecimal harvest = rebalance.harvestToReserve();
      // 9-10. Returned end states are the only next-year state.
      BigDecimal unfunded = remaining;
      BigDecimal rentalIncome =
          amount(
              longTermQuote.plannedCashFlows(),
              LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME);
      BigDecimal bondIncome =
          amount(
              longTermQuote.plannedCashFlows(),
              LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME);
      BigDecimal reserveEnd = reserveAfterTransfer.subtract(reserveWithdrawal).add(harvest);
      years.add(
          new Year(
              age,
              year,
              retired,
              expenses,
              employment,
              pension,
              events(input, year, SimulationEventType.ONE_OFF_INCOME),
              events(input, year, SimulationEventType.ONE_OFF_EXPENSE),
              rentalIncome.divide(BigDecimal.valueOf(12), 12, java.math.RoundingMode.HALF_UP),
              rentalIncome,
              bondIncome,
              cashFlow.netCashFlow().negate(),
              fundingGap,
              cashFlow.surplus(),
              reserveWithdrawal,
              longTermQuote.reserveTransfer(),
              longTermFunding.actualCapitalProvided(),
              longTermFunding.endCapital(),
              investment.withdrawal(),
              unfunded,
              reserveEnd,
              investment,
              longTermFunding.source(),
              harvest,
              longTermQuote.capitalizedBondReturn()));
      reserve = reserveEnd;
      investmentValue = investment.endValue();
      longTermState = longTermFunding.endState();
      if (retired) spending = spending.multiply(BigDecimal.ONE.add(input.spendingGrowthRate()));
    }
    return new Result(years);
  }

  private static List<PlannedCashFlow> lifecycleFlows(
      RetirementSimulationInput input,
      int year,
      BigDecimal expenses,
      BigDecimal employment,
      BigDecimal pension) {
    List<PlannedCashFlow> flows = new ArrayList<>();
    addAnnual(flows, "retirement-expenses", CashFlowDirection.EXPENSE, expenses, year);
    addAnnual(
        flows,
        "event-expenses",
        CashFlowDirection.EXPENSE,
        events(input, year, SimulationEventType.ONE_OFF_EXPENSE),
        year);
    addAnnual(flows, "employment", CashFlowDirection.INCOME, employment, year);
    addAnnual(flows, "pension", CashFlowDirection.INCOME, pension, year);
    addAnnual(
        flows,
        "event-income",
        CashFlowDirection.INCOME,
        events(input, year, SimulationEventType.ONE_OFF_INCOME),
        year);
    return flows;
  }

  private static void addLongTermFlows(
      List<PlannedCashFlow> flows,
      int year,
      List<LongTermAnnualProjectionApi.PlannedCashFlow> longTermFlows) {
    for (var flow : longTermFlows)
      addAnnual(flows, flow.id(), CashFlowDirection.INCOME, flow.annualAmount(), year);
  }

  private static BigDecimal amount(
      List<LongTermAnnualProjectionApi.PlannedCashFlow> flows,
      LongTermAnnualProjectionApi.CashFlowKind kind) {
    return flows.stream()
        .filter(flow -> flow.kind() == kind)
        .map(LongTermAnnualProjectionApi.PlannedCashFlow::annualAmount)
        .reduce(ZERO, BigDecimal::add);
  }

  private static LongTermAnnualProjectionApi.PlanningQuote applyIncomePolicy(
      LongTermAnnualProjectionApi.PlanningQuote source,
      int year,
      LongTermAnnualProjectionApi.PlanningState state,
      RetirementSimulationInput input) {
    var policy = input.fundingPolicy().projectedIncomePolicy();
    var flows = new ArrayList<>(source.plannedCashFlows());
    if (policy.rentalIncomeMode() == ProjectedIncomePolicy.IncomeMode.MANUAL) {
      flows.removeIf(flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME);
      BigDecimal base = policy.manualRentalIncome() == null ? ZERO : policy.manualRentalIncome();
      int baseYear =
          state.rentalIncomeBaseYear() > 0 ? state.rentalIncomeBaseYear() : input.startYear();
      BigDecimal amount =
          base.multiply(
              BigDecimal.ONE.add(state.rentalIncomeGrowthRate()).pow(Math.max(0, year - baseYear)));
      if (amount.signum() != 0)
        flows.add(
            new LongTermAnnualProjectionApi.PlannedCashFlow(
                "manual-rental",
                "Manual rental cash income",
                LongTermAnnualProjectionApi.CashFlowKind.RENTAL_INCOME,
                amount,
                source.source()));
    }
    if (policy.bondCashIncomeMode() == ProjectedIncomePolicy.IncomeMode.MANUAL) {
      flows.removeIf(flow -> flow.kind() == LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME);
      BigDecimal amount =
          policy.manualBondCashIncome() == null ? ZERO : policy.manualBondCashIncome();
      if (amount.signum() != 0)
        flows.add(
            new LongTermAnnualProjectionApi.PlannedCashFlow(
                "manual-bond-cash",
                "Manual bond cash income",
                LongTermAnnualProjectionApi.CashFlowKind.FIXED_INCOME,
                amount,
                source.source()));
    }
    return new LongTermAnnualProjectionApi.PlanningQuote(
        source.year(),
        flows,
        source.reserveTransfer(),
        source.capitalAvailable(),
        source.source(),
        source.capitalizedBondReturn());
  }

  private static BigDecimal events(
      RetirementSimulationInput input, int year, SimulationEventType type) {
    return input.events().stream()
        .filter(e -> e.year() == year && e.type() == type)
        .map(SimulationEvent::amount)
        .reduce(ZERO, BigDecimal::add);
  }

  private static void addAnnual(
      List<PlannedCashFlow> flows,
      String id,
      CashFlowDirection direction,
      BigDecimal amount,
      int year) {
    if (amount.signum() > 0)
      flows.add(
          new PlannedCashFlow(
              id,
              id,
              direction,
              CashFlowCadence.ANNUAL,
              amount,
              LocalDate.of(year, 1, 1),
              ProjectionSource.PROJECTED));
  }

  public record Result(List<Year> years) {
    public Result {
      years = List.copyOf(years);
    }
  }

  public record Year(
      int age,
      int year,
      boolean retired,
      BigDecimal expenses,
      BigDecimal employmentIncome,
      BigDecimal pensionIncome,
      BigDecimal eventIncome,
      BigDecimal eventExpenses,
      BigDecimal monthlyNetRentalIncome,
      BigDecimal annualRentalIncome,
      BigDecimal netBondIncome,
      BigDecimal incomeGap,
      BigDecimal requiredFunding,
      BigDecimal annualSurplus,
      BigDecimal reserveWithdrawal,
      BigDecimal reserveTransfer,
      BigDecimal maturedBondFunding,
      BigDecimal longTermCapitalEnd,
      BigDecimal investmentWithdrawal,
      BigDecimal unfundedShortfall,
      BigDecimal reserveEnd,
      InvestmentAnnualProjectionApi.AnnualProjection investment,
      LongTermAnnualProjectionApi.Source longTermSource,
      BigDecimal equityHarvestToReserve,
      BigDecimal capitalizedBondReturn) {}
}
