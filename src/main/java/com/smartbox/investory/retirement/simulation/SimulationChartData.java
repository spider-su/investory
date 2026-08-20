package com.smartbox.investory.retirement.simulation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SimulationChartData(
    Map<SimulationScenario, List<BalancePoint>> balances,
    List<IncomeSpendingPoint> incomeSpending,
    List<CompositionPoint> composition,
    Map<SimulationScenario, List<FundingPoint>> funding,
    Map<SimulationScenario, List<ReservePoint>> reserves) {
  public SimulationChartData(
      Map<SimulationScenario, List<BalancePoint>> balances,
      List<IncomeSpendingPoint> incomeSpending,
      List<CompositionPoint> composition) {
    this(balances, incomeSpending, composition, Map.of(), Map.of());
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
      boolean failed) {}

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
    results.forEach(
        (scenario, result) ->
            balances.put(
                scenario,
                result.years().stream()
                    .map(
                        y ->
                            new BalancePoint(
                                assumptions.startYear() + y.year(),
                                y.age(),
                                y.endNetWorth(),
                                y.spendableAssetsEnd()))
                    .toList()));
    results.forEach(
        (scenario, result) -> {
          funding.put(
              scenario,
              result.years().stream()
                  .map(
                      y ->
                          new FundingPoint(
                              assumptions.startYear() + y.year(),
                              y.age(),
                              y.passiveIncome(),
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
                              assumptions.startYear() + y.year(),
                              y.age(),
                              y.safeReserveEnd(),
                              y.safeReserveTarget(),
                              y.safeReserveCoverageYears(),
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
                        assumptions.startYear() + y.year(),
                        y.passiveIncome().add(y.pensionIncome()),
                        y.coreExpenses().add(y.discretionaryExpenses())))
            .toList(),
        base.stream()
            .map(
                y ->
                    new CompositionPoint(
                        assumptions.startYear() + y.year(),
                        y.cashEnd().add(y.manualLiquidReserveEnd()),
                        BigDecimal.ZERO,
                        y.fixedIncomeEnd().add(y.contractualAssetsEnd()),
                        y.equityEnd()))
            .toList(),
        funding,
        reserves);
  }
}
