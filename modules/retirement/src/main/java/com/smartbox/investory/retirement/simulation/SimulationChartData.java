package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SimulationChartData(
    Map<SimulationScenario, List<BalancePoint>> balances,
    List<IncomeSpendingPoint> incomeSpending,
    List<CompositionPoint> composition,
    Map<SimulationScenario, List<FundingPoint>> funding,
    Map<SimulationScenario, List<ReservePoint>> reserves,
    ChartMetadata metadata) {
  public SimulationChartData(
      Map<SimulationScenario, List<BalancePoint>> balances,
      List<IncomeSpendingPoint> incomeSpending,
      List<CompositionPoint> composition) {
    this(balances, incomeSpending, composition, Map.of(), Map.of(), ChartMetadata.empty());
  }

  public SimulationChartData(
      Map<SimulationScenario, List<BalancePoint>> balances,
      List<IncomeSpendingPoint> incomeSpending,
      List<CompositionPoint> composition,
      Map<SimulationScenario, List<FundingPoint>> funding,
      Map<SimulationScenario, List<ReservePoint>> reserves) {
    this(balances, incomeSpending, composition, funding, reserves, ChartMetadata.empty());
  }

  public record BalancePoint(int year, int age, BigDecimal netWorth, BigDecimal liquidAssets) {}

  /** Recurring decision flows only; one-off events remain available in the yearly projection. */
  public record IncomeSpendingPoint(
      int year, BigDecimal recurringIncome, BigDecimal plannedSpending) {}

  public record FundingPoint(
      int year,
      int age,
      BigDecimal passiveIncome,
      BigDecimal pensionIncome,
      BigDecimal plannedSpending,
      BigDecimal requiredPortfolioFunding,
      BigDecimal actualPortfolioWithdrawal,
      BigDecimal unfundedAmount) {}

  public record ReservePoint(
      int year,
      int age,
      BigDecimal safeReserveEnd,
      BigDecimal safeReserveTarget,
      BigDecimal safeReserveCoverageYears,
      BigDecimal safeReserveTargetCoverageYears,
      boolean failed) {}

  public record ChartMetadata(
      int retirementYear,
      int pensionStartYear,
      int horizonEndYear,
      int horizonEndAge,
      Map<SimulationScenario, FailureMarker> failures,
      List<Integer> expenseProfileTransitionYears) {
    public ChartMetadata {
      failures = failures == null ? Map.of() : Map.copyOf(failures);
      expenseProfileTransitionYears =
          expenseProfileTransitionYears == null
              ? List.of()
              : List.copyOf(expenseProfileTransitionYears);
    }

    static ChartMetadata empty() {
      return new ChartMetadata(0, 0, 0, 0, Map.of(), List.of());
    }
  }

  public record FailureMarker(int year, int age) {}

  /**
   * Simplified allocation view. Bonds include market fixed income and locked contractual
   * bonds/deposits.
   */
  public record CompositionPoint(
      int year, BigDecimal cash, BigDecimal apartments, BigDecimal bonds, BigDecimal equities) {}

  public static SimulationChartData from(
      Map<SimulationScenario, SimulationResult> results, SimulationAssumptions assumptions) {
    Map<SimulationScenario, List<BalancePoint>> balances =
        new java.util.EnumMap<>(SimulationScenario.class);
    Map<SimulationScenario, List<FundingPoint>> funding =
        new java.util.EnumMap<>(SimulationScenario.class);
    Map<SimulationScenario, List<ReservePoint>> reserves =
        new java.util.EnumMap<>(SimulationScenario.class);
    Map<SimulationScenario, FailureMarker> failures =
        new java.util.EnumMap<>(SimulationScenario.class);
    results.forEach(
        (scenario, result) -> {
          boolean failed = false;
          java.util.ArrayList<BalancePoint> points = new java.util.ArrayList<>();
          for (SimulationYear y : result.years()) {
            int calendarYear = y.year();
            if (y.failed() && !failed) {
              failures.put(scenario, new FailureMarker(calendarYear, y.age()));
              failed = true;
            }
            points.add(
                new BalancePoint(
                    calendarYear,
                    y.age(),
                    failed
                        ? (y.failed() ? y.endNetWorth().max(BigDecimal.ZERO) : null)
                        : y.endNetWorth(),
                    failed
                        ? (y.failed() ? y.spendableAssetsEnd().max(BigDecimal.ZERO) : null)
                        : y.spendableAssetsEnd()));
          }
          balances.put(scenario, List.copyOf(points));
        });
    results.forEach(
        (scenario, result) -> {
          funding.put(
              scenario,
              result.years().stream()
                  .map(
                      y ->
                          new FundingPoint(
                              y.year(),
                              y.age(),
                              y.totalIncome(),
                              y.pensionIncome(),
                              y.coreExpenses().add(y.discretionaryExpenses()),
                              y.requiredPortfolioFunding(),
                              y.actualPortfolioWithdrawal(),
                              y.unfundedAmount()))
                  .toList());
          reserves.put(
              scenario,
              result.years().stream()
                  .map(
                      y ->
                          new ReservePoint(
                              y.year(),
                              y.age(),
                              y.safeReserveEnd(),
                              y.safeReserveTarget(),
                              y.safeReserveCoverageYears(),
                              targetCoverage(y),
                              y.failed()))
                  .toList());
        });
    List<SimulationYear> base =
        results
            .getOrDefault(
                SimulationScenario.BASE,
                new SimulationResult(
                    SimulationScenario.BASE, false, null, BigDecimal.ZERO, List.of()))
            .years();
    return new SimulationChartData(
        balances,
        base.stream()
            .map(
                y ->
                    new IncomeSpendingPoint(
                        y.year(),
                        y.passiveIncome().add(y.pensionIncome()),
                        y.coreExpenses().add(y.discretionaryExpenses())))
            .toList(),
        base.stream()
            .map(
                y ->
                    new CompositionPoint(
                        y.year(),
                        y.cashEnd().add(y.manualLiquidReserveEnd()),
                        BigDecimal.ZERO,
                        y.fixedIncomeEnd().add(y.contractualAssetsEnd()),
                        y.equityEnd()))
            .toList(),
        funding,
        reserves,
        new ChartMetadata(
            assumptions.startYear() + assumptions.retirementAge() - assumptions.currentAge(),
            assumptions.startYear() + assumptions.pensionStartAge() - assumptions.currentAge(),
            assumptions.startYear() + assumptions.endAge() - assumptions.currentAge(),
            assumptions.endAge(),
            failures,
            assumptions.expenseProfile().steps().stream()
                .map(assumptions::expenseProfileStageYear)
                .toList()));
  }

  private static BigDecimal targetCoverage(SimulationYear year) {
    BigDecimal need = year.recurringFundingGap();
    return need == null || need.signum() <= 0
        ? null
        : year.safeReserveTarget().divide(need, 8, java.math.RoundingMode.HALF_UP);
  }
}
